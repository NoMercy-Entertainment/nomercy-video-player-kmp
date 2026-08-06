// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.ports.FetchOptions
import tv.nomercy.player.core.ports.FetchResponse
import tv.nomercy.player.video.subtitles.AssFontNames
import tv.nomercy.player.video.subtitles.FontManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tv.nomercy.player.core.events.CoreEvents
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.nomercy.player.video.ass.fonts.CachedFont
import tv.nomercy.player.video.ass.fonts.TwoTierFontCache
import tv.nomercy.player.video.subtitles.TtfNameParser

// Where a styled subtitle comes from and what has to happen before it is drawn.
//
// The order is the whole job. libass resolves a font at the moment it draws, so
// the subtitle has to be read first, the fonts it asks for fetched, those handed
// to the renderer, and only then the track loaded. Get it backwards and the cue
// renders in a fallback face with nothing reported — it looks right until
// someone who knows the show says the typeface is wrong.
//
// This is deliberately not the renderer. It fetches, parses and sequences; the
// renderer rasterizes. Splitting them is what lets the platform with no libass
// still parse a subtitle and know which fonts it would have needed.
public class SubtitlePlugin(
    private val renderer: AssRenderer,
    // Optional, because the cache needs a filesystem and only a host knows
    // where its cache directory is. Without one every episode re-downloads the
    // same three fonts, which works and is wasteful; with one the second
    // episode of a series registers from disk.
    private val fontCache: TwoTierFontCache? = null,
) : Plugin<SubtitleOptions>() {

    override val manifest: PluginManifest = SubtitleManifest

    // libass is not reentrant. Two threads inside it at once is not a wrong
    // pixel, it is a crash in native code — on Android a process death with a
    // stack that names none of this.
    //
    // The lock covers the native sequences and deliberately not the fetches. A
    // font download and a subtitle download should overlap; what must not
    // overlap is one operation's addFont-then-loadTrack landing inside
    // another's. Holding it across the network would serialise the downloads
    // too, which turns a slow font into a slow episode.
    private val nativeLock = Mutex()

    // The font files that were fetched and handed to the renderer, by file name.
    // Public because a host that pre-warms a cache wants the same list, and
    // because "which fonts arrived" is the first question when a subtitle looks
    // wrong.
    public var loadedFonts: List<String> = emptyList()
        private set

    // Returns false when the subtitle could not be read at all, so a caller can
    // fall back rather than sit in front of a player showing nothing.
    // The track that is loaded, by url, or null when none is.
    //
    // The reference calls this pair `subtitle`; this had only load(), so a
    // consumer could set a track and had no way to ask which one was showing.
    public fun subtitle(): String? = currentSubtitleUrl

    /**
     * Load a subtitle by url, or pass null to take it off.
     *
     * The reference's `subtitle(url)`. Null destroys the current track rather
     * than being ignored, which is how a viewer turns captions off.
     */
    public suspend fun subtitle(url: String?) {
        if (url == null) {
            clear()
            return
        }
        load(url, null)
    }

    /** The font files handed to the renderer, by the reference's name for them. */
    public fun fonts(): List<String> = loadedFonts

    /** The renderer this plugin is drawing through. */
    public fun renderer(): AssRenderer = renderer

    /**
     * Take the current track off and forget it.
     *
     * Separate from dispose: the plugin stays registered and ready for the next
     * track, which is what turning captions off means.
     */
    public suspend fun clear() {
        nativeLock.withLock {
            renderer.clearFonts()
            // An empty track is how this renderer is told to draw nothing;
            // there is no separate reset on the contract.
            renderer.loadTrack("")
        }
        currentSubtitleUrl = null
        loadedFonts = emptyList()
    }

    // The track does not survive the item that carried it.
    //
    // This plugin registered no listeners at all, so a track loaded for one film
    // stayed in the renderer when the queue moved on and libass went on drawing
    // that film's dialogue against the next one's playhead. Photographed on the
    // desktop testbed: Big Buck Bunny, a Blender short with no Japanese
    // dialogue, rendering an anime karaoke line in romaji over its opening.
    //
    // The sidecar CueTracker was fixed for exactly this and it is a different
    // renderer on a different path — clearing one said nothing about the other,
    // which is why the leak survived a fix that looked complete.
    override fun use() {
        // The SELECTED TRACK, not only the item.
        //
        // This overlay's lifecycle was bound to the queue: it was handed a track
        // when the item changed and heard nothing at all when the viewer changed
        // tracks inside it. So ASS to ASS worked — the consumer handed it the
        // new file — and ASS to off and ASS to WebVTT left the last rasterised
        // frame on screen with libass still holding the old track. A sign frozen
        // over a film that is no longer showing it.
        //
        // libass has no concept of "another renderer is drawing now"; only the
        // selection says so.
        //
        // Cleared unless the consumer answers the selection by handing this
        // plugin a different file. The url is captured before the launch and
        // compared after it, so a load() that lands in the same frame wins and
        // the track that was just installed is not wiped by the event that
        // caused it.
        on(CoreEvents.Subtitle) {
            // The url drops synchronously: subtitle() is what a chrome reads to
            // tick the current track, and leaving it set until libass caught up
            // would tick a file that is no longer being drawn.
            currentSubtitleUrl = null

            // The native teardown is launched, and skipped if the consumer has
            // meanwhile handed this plugin a new file. That is the ASS-to-ASS
            // case, which already worked and must keep working — clearing
            // unconditionally would wipe the track the selection just installed.
            pluginScope.launch { if (currentSubtitleUrl == null) clear() }
        }

        on(CoreEvents.Item) {
            // The url drops synchronously and the native work is launched.
            // `subtitle()` is what a consumer and the chrome read to decide
            // whether captions are on, and leaving it set until libass caught
            // up would leave a menu ticking a track for a film that no longer
            // has one.
            currentSubtitleUrl = null
            pluginScope.launch { clear() }
        }
    }

    // Launched rather than called: clear() suspends because it enters libass
    // under the lock, and an event listener that could suspend would colour
    // every emitter on this bus.
    private val pluginScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentSubtitleUrl: String? = null

    public suspend fun load(subtitleUrl: String, fontManifestUrl: String?): Boolean {
        currentSubtitleUrl = subtitleUrl
        val subtitle: String = get(subtitleUrl)?.body ?: run {
            // A false with nothing said. The caller can fall back, and everyone
            // else — a consumer's error surface, a support ticket — had no way
            // to learn the track never arrived.
            report(
                code = LOAD_FAILED,
                message = "no subtitle at $subtitleUrl; nothing was loaded",
                context = mapOf("url" to subtitleUrl),
            )
            return false
        }

        // Fonts first, and every one of them, before the track exists. A font
        // that arrives after the renderer has drawn once is a font libass has
        // already decided not to use.
        val fonts: List<ResolvedFont> =
            if (fontManifestUrl == null) emptyList() else fetchFonts(subtitle, fontManifestUrl)

        // One critical section for the whole sequence. Attaching and loading
        // are one operation from libass's point of view, and a late font
        // landing between them is a track loaded against a font set that was
        // still changing.
        nativeLock.withLock {
            // The previous item's fonts go first. They belong to the item they
            // came with, and libass resolves against whatever it holds — so an
            // episode whose manifest is missing a face would quietly draw in the
            // last episode's, which is a subtitle that looks fine to everyone
            // except someone who knows the show.
            renderer.clearFonts()

            val attached: MutableList<String> = mutableListOf()
            val seen: MutableSet<String> = mutableSetOf()
            for (font in fonts) {
                if (!seen.add(font.family.lowercase())) continue

                renderer.addFont(font.family, font.bytes)
                // By file name, which is what loadedFonts has always meant and
                // what a host pre-warming a cache matches against. The family is
                // what libass is told; the file name is what the manifest said.
                attached += font.fileName
            }
            loadedFonts = attached

            try {
                renderer.loadTrack(subtitle)
            }
            catch (@Suppress("TooGenericExceptionCaught") cause: Throwable) {
                // libass refusing a track is a rendering failure, not a missing
                // file, and it used to unwind out of here as whatever the
                // native layer threw.
                report(
                    code = RENDER_ERROR,
                    message = "libass could not load the track from $subtitleUrl",
                    cause = cause,
                    context = mapOf("url" to subtitleUrl),
                )
                return false
            }
            currentTrack = subtitle
        }
        return true
    }

    // Every font in the manifest, registered under the name it calls itself.
    //
    // Not under its filename, which is what this did first and what the comment
    // here used to defend. libass matches the family an ASS script asks for
    // against the name the font reports, and a file called Skeleton.ttf can
    // hold a family called anything — so a filename registration resolves to
    // nothing and the cue renders in a fallback face with nothing reporting it.
    // TtfNameParser reads the real name out of the font's own name table.
    //
    // Still every font in the manifest rather than the ones whose names look
    // like the families the cue asked for. A manifest is produced for one
    // subtitle, so its contents are what that subtitle needs; attaching one it
    // turns out not to use costs a download already paid for, and attaching
    // none because a name did not match costs the wrong typeface for the whole
    // film. The cue's own font list is read only to skip the step entirely when
    // there is nothing to fetch.
    // Downloads every font the manifest names and resolves what to call it.
    //
    // No native calls here at all, which is the point of splitting it out: the
    // network is slow and libass is exclusive, and holding the lock across a
    // download would make one slow font into a stalled episode.
    //
    // Every font in the manifest rather than the ones whose names look like the
    // families the cue asked for. A manifest is produced for one subtitle, so
    // its contents are what that subtitle needs; attaching one it turns out not
    // to use costs a download already paid for, and attaching none because a
    // name did not match costs the wrong typeface for the whole film.
    private suspend fun fetchFonts(subtitle: String, manifestUrl: String): List<ResolvedFont> {
        if (AssFontNames.parse(subtitle).isEmpty()) return emptyList()

        val manifest: Map<String, String> = get(manifestUrl)?.body
            ?.let { FontManifest.parse(it, manifestUrl) }
            .orEmpty()
        if (manifest.isEmpty()) {
            reportFontsUnavailable(manifestUrl)
            return emptyList()
        }

        val resolved: MutableList<ResolvedFont> = mutableListOf()
        for ((fileName, url) in manifest) {
            // The cache first. A series attaches the same fonts to every
            // episode, so after the first one this is a disk read rather than a
            // download — and after the first play in a session, nothing at all.
            val cached: CachedFont? = fontCache?.get(fileName)
            val bytes: ByteArray? = cached?.bytes ?: get(url)?.bytes
            if (bytes == null) {
                // Said out loud rather than skipped.
                //
                // This was `?: continue`, so a fetcher that answers with text
                // and no bytes lost every face without a word: the track loaded,
                // load() returned true, and the cue drew in a fallback typeface.
                // A font that cannot be read is the difference between the show
                // as authored and something that merely looks like it.
                reportFontUnreadable(fileName, url)
                continue
            }

            // Under the family, not the filename. libass matches the family an
            // ASS script asks for against the name the font reports, and a file
            // called Skeleton.ttf can hold a family called anything.
            val family: String = cached?.registerName
                ?: TtfNameParser.extractFontName(bytes, TtfNameParser.fallbackNameFor(fileName))

            if (cached == null) fontCache?.put(fileName, bytes)
            resolved += ResolvedFont(fileName = fileName, family = family, bytes = bytes)
        }
        return resolved
    }

    // A font that turned up after the track was already loaded.
    //
    // The ordinary path attaches everything first, because libass resolves a
    // family once and a font arriving later is one it has already decided not to
    // use. When one does arrive late anyway — a slow download, a server that
    // found the file after the episode started — adding it is not enough: the
    // track has to be handed back so libass resolves the families again.
    //
    // Reloading is cheap and visible. Not reloading is a film that plays to the
    // end in a fallback typeface with nothing reporting why.
    public suspend fun addFontLate(fileName: String, data: ByteArray) {
        val family: String = TtfNameParser.extractFontName(data, TtfNameParser.fallbackNameFor(fileName))

        // The same critical section load() uses. Registering a font and handing
        // the track back is one operation to libass; split across another's,
        // the track is resolved against a font set still being written.
        nativeLock.withLock {
            renderer.addFont(family, data)
            loadedFonts = loadedFonts + fileName

            // Only when there is a track to reload. Before one exists this is
            // the ordinary path with a different name.
            currentTrack?.let(renderer::loadTrack)
        }
    }

    // Reported rather than thrown, and reported rather than swallowed.
    //
    // libass falls back to system fonts when it has none of its own, so the
    // film plays and the subtitles are legible — just in the wrong typeface.
    // Throwing would take a watchable episode away over a cosmetic problem;
    // saying nothing is what makes "the subtitles look wrong" unanswerable a
    // week later.
    private fun reportFontUnreadable(fileName: String, url: String) {
        report(
            code = FONT_UNREADABLE,
            message = "no bytes for $fileName at $url; the cue will draw in a fallback face",
        )
    }

    private fun reportFontsUnavailable(manifestUrl: String) {
        report(
            code = FONTS_MANIFEST_FAILED,
            message = "no font manifest at $manifestUrl; libass will use system fonts",
        )
    }

    private var currentTrack: String? = null

    private suspend fun get(url: String): FetchResponse? {
        val response: FetchResponse = runCatching { fetch(url, FetchOptions()) }.getOrNull() ?: return null
        return if (response.status in OK_RANGE) response else null
    }

    override fun dispose() {
        pluginScope.cancel()
        renderer.release()
    }

    private companion object {
        val OK_RANGE = 200..299

        // The web's namespace, not this plugin's own.
        //
        // These read plugin:subtitle/* here and plugin:octopus/* everywhere
        // else, which is one failure a consumer has to match twice. The web
        // plugin is named for the library it wraps and this one is not, but a
        // renderer's name is a poor reason to give its failures two spellings —
        // the same argument the video-namespaced HDR code settles the same way.
        const val FONTS_MANIFEST_FAILED = "plugin:octopus/fonts-manifest-failed"
        const val LOAD_FAILED = "plugin:octopus/load-failed"
        const val RENDER_ERROR = "plugin:octopus/render-error"

        // No web counterpart: the browser renderer is handed fonts by the page
        // and never reports one it could not read.
        const val FONT_UNREADABLE = "plugin:subtitle/font-unreadable"
    }
}

// A font on its way to libass, carrying both names it is known by.
//
// The manifest names the file and libass wants the family, and the two are
// different strings — losing the file name is how loadedFonts came to report
// families and broke a host matching against what it asked for.
private class ResolvedFont(
    val fileName: String,
    val family: String,
    val bytes: ByteArray,
)

// Nothing yet, and named rather than Unit so adding one later is not a
// signature change for every consumer.
public class SubtitleOptions

private object SubtitleManifest : PluginManifest {
    override val id: String = "nomercy:subtitles-ass"
    override val version: String = "0.1.0"
}
