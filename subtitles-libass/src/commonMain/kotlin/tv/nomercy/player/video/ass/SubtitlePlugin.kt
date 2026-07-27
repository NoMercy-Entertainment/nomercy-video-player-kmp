// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.ports.FetchOptions
import tv.nomercy.player.core.ports.FetchResponse
import tv.nomercy.player.video.subtitles.AssFontNames
import tv.nomercy.player.video.subtitles.FontManifest
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
    public suspend fun load(subtitleUrl: String, fontManifestUrl: String?): Boolean {
        val subtitle: String = get(subtitleUrl)?.body ?: return false

        // Fonts first, and every one of them, before the track exists. A font
        // that arrives after the renderer has drawn once is a font libass has
        // already decided not to use.
        val fonts: List<Pair<String, ByteArray>> =
            if (fontManifestUrl == null) emptyList() else fetchFonts(subtitle, fontManifestUrl)

        // One critical section for the whole sequence. Attaching and loading
        // are one operation from libass's point of view, and a late font
        // landing between them is a track loaded against a font set that was
        // still changing.
        nativeLock.withLock {
            val attached: MutableList<String> = mutableListOf()
            val seen: MutableSet<String> = mutableSetOf()
            for ((family, bytes) in fonts) {
                if (!seen.add(family.lowercase())) continue

                renderer.addFont(family, bytes)
                attached += family
            }
            loadedFonts = attached

            renderer.loadTrack(subtitle)
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
    private suspend fun fetchFonts(subtitle: String, manifestUrl: String): List<Pair<String, ByteArray>> {
        if (AssFontNames.parse(subtitle).isEmpty()) return emptyList()

        val manifest: Map<String, String> = get(manifestUrl)?.body
            ?.let { FontManifest.parse(it, manifestUrl) }
            .orEmpty()
        if (manifest.isEmpty()) {
            reportFontsUnavailable(manifestUrl)
            return emptyList()
        }

        val resolved: MutableList<Pair<String, ByteArray>> = mutableListOf()
        for ((fileName, url) in manifest) {
            // The cache first. A series attaches the same fonts to every
            // episode, so after the first one this is a disk read rather than a
            // download — and after the first play in a session, nothing at all.
            val cached: CachedFont? = fontCache?.get(fileName)
            val bytes: ByteArray = cached?.bytes ?: get(url)?.bytes ?: continue

            // Under the family, not the filename. libass matches the family an
            // ASS script asks for against the name the font reports, and a file
            // called Skeleton.ttf can hold a family called anything.
            val family: String = cached?.registerName
                ?: TtfNameParser.extractFontName(bytes, TtfNameParser.fallbackNameFor(fileName))

            if (cached == null) fontCache?.put(fileName, bytes)
            resolved += family to bytes
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
        renderer.release()
    }

    private companion object {
        val OK_RANGE = 200..299

        // Namespaced the way every other player error is, so a host filtering
        // by scope catches it without knowing this plugin exists.
        const val FONTS_MANIFEST_FAILED = "plugin:subtitle/fonts-manifest-failed"
    }
}

// Nothing yet, and named rather than Unit so adding one later is not a
// signature change for every consumer.
public class SubtitleOptions

private object SubtitleManifest : PluginManifest {
    override val id: String = "nomercy:subtitles-ass"
    override val version: String = "0.1.0"
}
