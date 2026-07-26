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

    // The fonts a cue actually asked for, in the order they were found, after
    // the manifest said where they live. Public because a host that pre-warms a
    // cache wants the same list.
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
        return true
    }

    private suspend fun attachFonts(subtitle: String, manifestUrl: String) {
        val manifest: Map<String, String> = get(manifestUrl)?.body
            ?.let { FontManifest.parse(it, manifestUrl) }
            .orEmpty()
        if (manifest.isEmpty()) return

        val wanted: List<String> = AssFontNames.parse(subtitle)
        val attached: MutableList<String> = mutableListOf()

        for (name in wanted) {
            val url: String = manifest[fileKeyFor(name, manifest)] ?: continue
            val bytes: ByteArray = get(url)?.bytes ?: continue
            renderer.addFont(name, bytes)
            attached += name
        }
        loadedFonts = attached
    }

    // The manifest is keyed by file name and a cue names a family, and the two
    // agree often enough to be worth trying and not often enough to rely on. A
    // miss is a font that is not fetched, which is a fallback typeface rather
    // than a failure — so it is skipped quietly rather than reported as an
    // error the viewer cannot act on.
    private fun fileKeyFor(fontName: String, manifest: Map<String, String>): String {
        val normalized: String = fontName.lowercase().replace(" ", "")
        return manifest.keys.firstOrNull { key ->
            key.substringBeforeLast('.').lowercase().replace(" ", "").replace("-", "") ==
                normalized.replace("-", "")
        } ?: ""
    }

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
