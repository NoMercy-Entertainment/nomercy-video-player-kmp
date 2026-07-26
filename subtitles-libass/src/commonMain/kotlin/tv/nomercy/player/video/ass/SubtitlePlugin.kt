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
) : Plugin<SubtitleOptions>() {

    override val manifest: PluginManifest = SubtitleManifest

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
        if (fontManifestUrl != null) {
            attachFonts(subtitle, fontManifestUrl)
        }

        renderer.loadTrack(subtitle)
        currentTrack = subtitle
        return true
    }

    // Every font in the manifest, not the ones whose filenames look like the
    // families the cue asked for.
    //
    // Matching a family to a file name cannot be done from the outside: libass
    // resolves against the family recorded inside the font data, and a file
    // called Skeleton.ttf can hold a family called anything. Guessing gets it
    // wrong quietly — the cue renders in a fallback face and nothing reports it.
    //
    // A manifest is produced for one subtitle, so its contents are the fonts
    // that subtitle needs; attaching one it turns out not to use costs a
    // download it already paid for, and attaching none because a name did not
    // match costs the wrong typeface for the whole film. The cue's own font list
    // is still read, but only to skip the step entirely when there is nothing to
    // fetch.
    private suspend fun attachFonts(subtitle: String, manifestUrl: String) {
        if (AssFontNames.parse(subtitle).isEmpty()) return

        val manifest: Map<String, String> = get(manifestUrl)?.body
            ?.let { FontManifest.parse(it, manifestUrl) }
            .orEmpty()
        if (manifest.isEmpty()) return

        val attached: MutableList<String> = mutableListOf()
        for ((fileName, url) in manifest) {
            val bytes: ByteArray = get(url)?.bytes ?: continue
            renderer.addFont(fileName, bytes)
            attached += fileName
        }
        loadedFonts = attached
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
    public fun addFontLate(fileName: String, data: ByteArray) {
        renderer.addFont(fileName, data)
        loadedFonts = loadedFonts + fileName

        // Only when there is a track to reload. Before one exists this is just
        // the ordinary path with a different name.
        currentTrack?.let(renderer::loadTrack)
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
    }
}

// Nothing yet, and named rather than Unit so adding one later is not a
// signature change for every consumer.
public class SubtitleOptions

private object SubtitleManifest : PluginManifest {
    override val id: String = "nomercy:subtitles-ass"
    override val version: String = "0.1.0"
}
