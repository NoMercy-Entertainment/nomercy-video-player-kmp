// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.FetchResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val OK = 200
private const val NOT_FOUND = 404
private const val SUBTITLE_URL = "https://media.example.test/show/1/subs/en.ass"
private const val MANIFEST_URL = "https://media.example.test/show/1/fonts/fonts.json"
private val FONT_BYTES = byteArrayOf(0, 1, 2, 3)

// The plugin against a real player, with only the network and libass stood in
// for — which are the two things a test cannot have and the two things this
// plugin is not responsible for.
class SubtitlePluginTest {

    private fun player(responses: Map<String, FetchResponse>): Pair<ComposedPlayer, MutableList<String>> {
        val requested: MutableList<String> = mutableListOf()
        val player = ComposedPlayer(
            backend = null,
            fetcher = { url, _ ->
                requested += url
                responses[url] ?: FetchResponse(status = NOT_FOUND)
            },
        )
        return player to requested
    }

    private suspend fun install(player: ComposedPlayer, renderer: AssRenderer): SubtitlePlugin {
        val plugin = SubtitlePlugin(renderer)
        player.setup(PlayerConfig())
        player.addPlugin(plugin)
        return plugin
    }

    @Test
    fun everyFontIsAttachedBeforeTheTrackIsLoaded() = runTest {
        // The ordering that fails silently. libass resolves a font when it
        // draws, so a track loaded first renders in a fallback face and reports
        // nothing — it looks right until someone who knows the show says the
        // typeface is wrong.
        val renderer = RecordingRenderer()
        val (player, _) = player(
            mapOf(
                SUBTITLE_URL to FetchResponse(status = OK, body = skeletonAss("Skeleton Sans")),
                MANIFEST_URL to FetchResponse(status = OK, body = """["SkeletonSans.ttf"]"""),
                "https://media.example.test/show/1/fonts/SkeletonSans.ttf" to
                    FetchResponse(status = OK, bytes = FONT_BYTES),
            ),
        )
        val plugin = install(player, renderer)

        plugin.load(SUBTITLE_URL, MANIFEST_URL)

        // Lowercased, because the manifest parser keys by file name in a
        // single case — the name a producer writes and the name a cue implies
        // differ in capitalisation often enough that matching on it would fail
        // for cosmetic reasons.
        assertEquals(listOf("addFont:skeletonsans.ttf", "loadTrack"), renderer.calls)
        assertTrue(FONT_BYTES.contentEquals(renderer.fonts.getValue("skeletonsans.ttf")))
    }

    @Test
    fun aSubtitleThatCannotBeFetchedIsSaidSoRatherThanLoadedEmpty() = runTest {
        val renderer = RecordingRenderer()
        val (player, _) = player(emptyMap())
        val plugin = install(player, renderer)

        val loaded: Boolean = plugin.load(SUBTITLE_URL, MANIFEST_URL)

        assertEquals(false, loaded)
        assertEquals(emptyList(), renderer.calls, "a track was loaded from a subtitle that never arrived")
    }

    @Test
    fun aMissingFontManifestStillLoadsTheSubtitle() = runTest {
        // A subtitle in a fallback typeface is worse than one in the right
        // typeface, and far better than none.
        val renderer = RecordingRenderer()
        val (player, _) = player(
            mapOf(SUBTITLE_URL to FetchResponse(status = OK, body = skeletonAss("Skeleton Sans"))),
        )
        val plugin = install(player, renderer)

        val loaded: Boolean = plugin.load(SUBTITLE_URL, MANIFEST_URL)

        assertEquals(true, loaded)
        assertEquals(listOf("loadTrack"), renderer.calls)
    }

    @Test
    fun noFontsAreFetchedWhenTheCueAsksForNone() = runTest {
        // Arial is skipped on purpose everywhere in this pipeline: it is
        // libass's own fallback, so fetching it costs a request that can only
        // return what was going to be used anyway.
        val renderer = RecordingRenderer()
        val (player, requested) = player(
            mapOf(
                SUBTITLE_URL to FetchResponse(status = OK, body = skeletonAss("Arial")),
                MANIFEST_URL to FetchResponse(status = OK, body = """["Arial.ttf"]"""),
            ),
        )
        val plugin = install(player, renderer)

        plugin.load(SUBTITLE_URL, MANIFEST_URL)

        assertEquals(listOf("loadTrack"), renderer.calls)
        assertTrue(requested.none { it.endsWith(".ttf") }, "a font was fetched for a cue that named none")
    }

    @Test
    fun disposingThePluginReleasesTheRenderer() = runTest {
        val renderer = RecordingRenderer()
        val (player, _) = player(emptyMap())
        install(player, renderer)

        player.dispose()

        assertTrue(renderer.released, "the renderer outlived the plugin that owned it")
    }
}
