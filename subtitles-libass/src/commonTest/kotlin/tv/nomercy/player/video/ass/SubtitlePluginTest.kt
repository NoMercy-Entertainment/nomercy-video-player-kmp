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
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import tv.nomercy.player.video.ass.fonts.TwoTierFontCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val OK = 200
private const val NOT_FOUND = 404
private const val SUBTITLE_URL = "https://media.example.test/show/1/subs/en.ass"
private const val MANIFEST_URL = "https://media.example.test/show/1/fonts/fonts.json"
private val FONT_BYTES = byteArrayOf(0, 1, 2, 3)
private const val FONT_URL = "https://media.example.test/show/1/fonts/SkeletonSans.ttf"

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

    private suspend fun install(
        player: ComposedPlayer,
        renderer: AssRenderer,
        cache: TwoTierFontCache? = null,
    ): SubtitlePlugin {
        val plugin = SubtitlePlugin(renderer, cache)
        player.setup(PlayerConfig())
        player.addPlugin(plugin)
        return plugin
    }

    @Test
    fun theSecondEpisodeRegistersFromCacheRatherThanDownloadingAgain() {
        // A series attaches the same three fonts to every episode. Without a
        // cache that is the same download twelve times over a season, on a
        // connection the viewer is also streaming video across.
        runTest {
            val cache = TwoTierFontCache(FakeFileSystem(), "/cache".toPath())
            val responses = mapOf(
                SUBTITLE_URL to FetchResponse(status = OK, body = skeletonAss("Skeleton Sans")),
                MANIFEST_URL to FetchResponse(status = OK, body = """["SkeletonSans.ttf"]"""),
                FONT_URL to FetchResponse(status = OK, bytes = FONT_BYTES),
            )

            val (first, firstRequests) = player(responses)
            install(first, RecordingRenderer(), cache).load(SUBTITLE_URL, MANIFEST_URL)
            assertTrue(firstRequests.contains(FONT_URL), "the first episode never fetched the font")

            val (second, secondRequests) = player(responses)
            val renderer = RecordingRenderer()
            install(second, renderer, cache).load(SUBTITLE_URL, MANIFEST_URL)

            assertFalse(secondRequests.contains(FONT_URL), "the font was downloaded a second time")
            // And it still reached libass — a cache that serves nothing is
            // indistinguishable from one that works, by request count alone.
            assertTrue(renderer.fonts.isNotEmpty(), "the cached font never reached the renderer")
        }
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
        // Under the family, not the filename. libass matches the family an ASS
        // script asks for against the name the font reports, and a file called
        // Skeleton.ttf can hold a family called anything — the shipped Android
        // app registers via TtfNameParser for exactly this reason. These bytes
        // are not a real font, so the parser falls back to the filename with
        // its extension stripped, which is what a real font's name would
        // replace.
        assertEquals(listOf("addFont:skeletonsans", "loadTrack"), renderer.calls)
        assertTrue(FONT_BYTES.contentEquals(renderer.fonts.getValue("skeletonsans")))
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

    private suspend fun loadedPlugin(renderer: RecordingRenderer): SubtitlePlugin {
        val (player, _) = player(
            mapOf(SUBTITLE_URL to FetchResponse(status = OK, body = skeletonAss("Skeleton Sans"))),
        )
        return install(player, renderer).also { it.load(SUBTITLE_URL, fontManifestUrl = null) }
    }

    @Test
    fun aFontThatArrivesLateMakesLibassResolveTheFamiliesAgain() = runTest {
        // libass resolves a family once. Adding a font afterwards without
        // handing the track back leaves the film playing to the end in a
        // fallback typeface with nothing reporting why.
        val renderer = RecordingRenderer()
        val plugin: SubtitlePlugin = loadedPlugin(renderer)
        val loadsBefore: Int = renderer.calls.count { it.startsWith("loadTrack") }

        plugin.addFontLate("LateArrival.ttf", byteArrayOf(1, 2, 3))

        assertEquals(loadsBefore + 1, renderer.calls.count { it.startsWith("loadTrack") })
        assertTrue(renderer.fonts.containsKey("LateArrival"))
        assertTrue(plugin.loadedFonts.contains("LateArrival.ttf"))
    }

    @Test
    fun aLateFontBeforeAnyTrackDoesNotReloadNothing() = runTest {
        // Before a track exists this is just the ordinary path, and reloading
        // would hand libass an empty string.
        val renderer = RecordingRenderer()
        val (player, _) = player(emptyMap())

        install(player, renderer).addFontLate("Early.ttf", byteArrayOf(1))

        assertEquals(0, renderer.calls.count { it.startsWith("loadTrack") })
        assertTrue(renderer.fonts.containsKey("Early"))
    }

}
