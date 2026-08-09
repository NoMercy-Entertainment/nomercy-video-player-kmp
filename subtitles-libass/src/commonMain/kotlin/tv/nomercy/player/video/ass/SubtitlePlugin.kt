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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.ports.SubtitleTrack
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
        currentSubtitleUrl = null
        clearTrack()
    }

    // The teardown without the bookkeeping, for the caller that has already
    // recorded what is wanted.
    //
    // `clear()` used to write the url null AFTER awaiting the native lock, and
    // that write is a claim about the present made by a coroutine that started
    // in the past. Turning captions off and straight back on: the teardown
    // reached the lock, the reselection wrote the styled url, the teardown
    // finished and set null over it, and the reconcile that would have loaded
    // the track then read a url that no longer matched its own target and did
    // nothing. Captions off then on left the screen dark and the menu ticking
    // nothing -- the same defect this class was written for, arriving through
    // the one door it did not cover.
    private suspend fun clearTrack() {
        nativeLock.withLock {
            renderer.clearFonts()
            // An empty track is how this renderer is told to draw nothing;
            // there is no separate reset on the contract.
            renderer.loadTrack("")
        }
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
    // One pipeline, fed by the two event families that can change what should
    // be on screen, and a single place that decides.
    //
    // The listeners do not act. They update what is KNOWN — the track list, the
    // selected index, the item — and then ask for a reconcile, which compares
    // what should be drawn against what is drawn and moves one to the other.
    // Every earlier shape of this acted directly from a listener and each one
    // handled the case it was written for: the item change cleared, and then
    // turning captions off did not; the selection cleared, and then turning them
    // back on did not, because nothing loaded except on an item change. A rule
    // per transition is a rule missing per transition nobody has hit yet.
    override fun use() {
        // The list, so a selection index can be resolved to a file. Reconciling
        // on it only once a selection has been seen: before that the list
        // arriving would compute "nothing selected" and take down a track a
        // consumer had loaded by hand.
        on(CoreEvents.Subtitles) { payload ->
            available = payload.tracks.filterIsInstance<SubtitleTrack>()
            if (selectionSeen) reconcile()
        }

        // Which one, or none. libass has no concept of another renderer drawing
        // now, so a selection this plugin cannot draw reads here as "draw
        // nothing" — which is exactly right, and is the WebVTT case.
        on(CoreEvents.Subtitle) { payload ->
            selected = payload.track?.toInt()
            selectionSeen = true
            reconcile()
        }

        // A new film's SELECTION is unknown until it announces one. The list is
        // not cleared here, and that ordering is the whole of it: the player
        // emits the new item's tracks from inside the `item` dispatch, so the
        // list has already arrived by the time this runs. Clearing it here threw
        // the new item's tracks away, `wanted()` could never resolve one, and
        // every reconcile computed null both before and after — which is a
        // selection change that correctly does nothing, and looks on screen
        // exactly like the raster never clearing.
        on(CoreEvents.Item) {
            selected = null
            selectionSeen = false
            manifestUrl = null
            reconcile()
        }
    }

    // What should be drawn, given everything currently known.
    //
    // Null when captions are off, when the selection belongs to another
    // renderer, or when nothing is known yet.
    private fun wanted(): String? {
        val index: Int = selected ?: return null
        val track: SubtitleTrack = available.getOrNull(index) ?: return null
        return track.url?.takeIf { styled(track) }
    }

    // Format first, because that is what the track declares; the extension is
    // the fallback for a server that sends none.
    private fun styled(track: SubtitleTrack): Boolean {
        val format: String = track.format.lowercase()
        if (format == "ass" || format == "ssa") return true
        val url: String = track.url?.lowercase() ?: return false
        return url.endsWith(".ass") || url.endsWith(".ssa")
    }

    // Move what is drawn to what should be.
    //
    // The url is written synchronously because subtitle() is what a chrome reads
    // to tick the current track, and a menu ticking a file nothing is drawing is
    // the same lie in the other direction. The native work is launched, and
    // re-checks before it acts: a second selection arriving while the first is
    // still loading must not leave the loser installed.
    private fun reconcile() {
        val target: String? = wanted()
        if (target == currentSubtitleUrl) return

        currentSubtitleUrl = target

        // Queued behind the previous reconcile, not racing it and not cancelling
        // it.
        //
        // Two selections in quick succession — off then back on, which is the
        // whole of turning captions off and on again — launched two coroutines
        // on the same scope with nothing ordering them, so a clear could land
        // after a load and wipe the track that had just been installed.
        // Cancelling the older one instead was worse: load() is half a dozen
        // awaits and a cancel in the middle leaves the plugin holding a url for
        // a track that never finished arriving.
        //
        // reconcileLock is separate from nativeLock, which guards libass itself
        // and is taken inside load() and clear(). Reusing it here would mean a
        // reconcile held it across its own fetches, which is the thing that lock
        // exists not to do.
        pluginScope.launch {
            reconcileLock.withLock {
                // The last word wins. An older reconcile that waited its turn
                // must not undo the selection made while it waited.
                if (currentSubtitleUrl != target) return@withLock
                if (target == null) clearTrack() else loadTrack(target, manifestUrl)
            }
        }
    }

    private val reconcileLock = Mutex()
    private var available: List<SubtitleTrack> = emptyList()
    private var selected: Int? = null
    private var selectionSeen: Boolean = false

    // The font manifest the consumer named for this item, remembered so a
    // reselection inside the same film gets the same faces. libass resolves a
    // face when it draws, so a track reloaded without them renders in a fallback
    // and nothing reports a problem.
    private var manifestUrl: String? = null

    // Where the reconcile coroutines run.
    //
    // Settable, and internal rather than a constructor parameter: a parameter
    // would change the public constructor for every consumer to serve a
    // question only this module's own tests ask, and the dump says so — the
    // signature moves and every compiled call site with it.
    //
    // It has to be settable at all because Dispatchers.Default is a scheduler no
    // test can drive. The selection tests emitted, asserted, and were right only
    // if the launched work happened to have run by then; that landed badly about
    // one CI run in five, on a different assertion each time, which is what a
    // race looks like from the outside. Given the test dispatcher,
    // `advanceUntilIdle()` means what it says.
    internal var dispatcher: CoroutineDispatcher = Dispatchers.Default

    // Built on first use, so setting the dispatcher before the first event is
    // enough. Constructing it eagerly would capture Dispatchers.Default before a
    // test could say otherwise, which is the whole point of the property.
    //
    // Launched rather than called: clear() suspends because it enters libass
    // under the lock, and an event listener that could suspend would colour
    // every emitter on this bus.
    private val pluginScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + dispatcher) }

    private var currentSubtitleUrl: String? = null

    public suspend fun load(subtitleUrl: String, fontManifestUrl: String?): Boolean {
        currentSubtitleUrl = subtitleUrl
        return loadTrack(subtitleUrl, fontManifestUrl)
    }

    // The load without the bookkeeping. Same reason as [clearTrack]: what is
    // wanted is decided before the work starts, and a coroutine that has been
    // waiting has nothing current to say about it.
    private suspend fun loadTrack(subtitleUrl: String, fontManifestUrl: String?): Boolean {
        // Remembered, so a reselection inside the same film reloads with the
        // faces the consumer named rather than without them.
        manifestUrl = fontManifestUrl
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
                // Under every name the face answers to, and the dedupe is per
                // name rather than per file. One file carries a full name and a
                // family, a style line names one of the two, and registering
                // only the winner is how a script asking for "Fontin Sans Rg"
                // got a face registered as "FontinSans-Bold" and drew in
                // something else entirely.
                val fresh: List<String> = font.families.filter { seen.add(it.lowercase()) }
                if (fresh.isEmpty()) continue

                for (name in fresh) renderer.addFont(name, font.bytes)
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

            // Under the names the font reports, not the filename. libass
            // matches the family an ASS script asks for against the name the
            // font reports, and a file called Skeleton.ttf can hold a family
            // called anything.
            //
            // Read from the bytes even when the cache has a name for them: the
            // cache stores one, and one is what this defect was.
            val families: List<String> =
                TtfNameParser.extractFontNames(bytes, TtfNameParser.fallbackNameFor(fileName))

            if (cached == null) fontCache?.put(fileName, bytes)
            resolved += ResolvedFont(fileName = fileName, families = families, bytes = bytes)
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
        val families: List<String> = TtfNameParser.extractFontNames(data, TtfNameParser.fallbackNameFor(fileName))

        // The same critical section load() uses. Registering a font and handing
        // the track back is one operation to libass; split across another's,
        // the track is resolved against a font set still being written.
        nativeLock.withLock {
            for (name in families) renderer.addFont(name, data)
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
    val families: List<String>,
    val bytes: ByteArray,
)

// Nothing yet, and named rather than Unit so adding one later is not a
// signature change for every consumer.
public class SubtitleOptions

private object SubtitleManifest : PluginManifest {
    override val id: String = "nomercy:subtitles-ass"
    override val version: String = "0.1.0"
}
