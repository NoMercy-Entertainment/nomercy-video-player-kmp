// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssImage
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.FetchResponse
import kotlin.test.Test
import kotlin.test.assertTrue

private const val OK = 200
private const val NOT_FOUND = 404
private const val SUBTITLE_URL = "https://media.example.test/rail/subtitles/rail.ass"
private const val MANIFEST_URL = "https://media.example.test/rail/fonts.json"
private const val FONT_URL = "https://media.example.test/rail/fonts/negotiate.ttf"

private const val FRAME_WIDTH = 1_920
private const val FRAME_HEIGHT = 1_080
private const val MID_FRAME = FRAME_HEIGHT / 2

// A real anime track, through the real pipeline, on whatever renderer this
// target actually has.
//
// Every other test in this module stands something in: the plugin tests fake
// libass, the render gates fake the pipeline. This fakes only the network, so a
// failure here means the subsystem is broken rather than a seam being wrong —
// and it runs from shared source, so the same assertions are answered by the JNI
// binding on Android, JNA on the desktop and cinterop on iOS and tvOS.
//
// What it asserts is what the format is FOR. "libass loaded" is not the claim:
// a track that renders a blank frame satisfies that. These are fade, colour
// transform, and two styles landing on opposite halves of the screen.
class SubtitleSubsystemGateTest {

    private fun player(vararg missing: String): ComposedPlayer {
        val responses: Map<String, FetchResponse> = mapOf(
            SUBTITLE_URL to FetchResponse(status = OK, body = RAIL_ASS),
            MANIFEST_URL to FetchResponse(status = OK, body = RAIL_FONT_MANIFEST),
            FONT_URL to FetchResponse(status = OK, bytes = railFontBytes()),
        )

        return ComposedPlayer(
            backend = null,
            fetcher = { url, _ ->
                if (url in missing) FetchResponse(status = NOT_FOUND)
                else responses[url] ?: FetchResponse(status = NOT_FOUND)
            },
        )
    }

    private suspend fun rendered(atMillis: Long, vararg missing: String): List<AssImage>? {
        val renderer: AssRenderer = newTestRenderer() ?: return null
        val host: ComposedPlayer = player(*missing)
        val plugin = SubtitlePlugin(renderer)

        host.setup(PlayerConfig())
        host.addPlugin(plugin)
        renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
        plugin.load(SUBTITLE_URL, MANIFEST_URL)

        return renderer.render(atMillis)?.images.orEmpty()
    }

    private fun coverage(images: List<AssImage>): Int =
        images.sumOf { image -> image.pixels.count { it.toInt() != 0 } }

    @Test
    fun theOpeningVerseRastersGlyphsPartWayThroughItsFade() = runTest {
        // 11 seconds is inside the 9.03 to 13.95 cue and well past its 150ms
        // fade-in, so the text is up and solid. A subsystem that loaded the
        // track and drew nothing passes every other test in this module.
        val images: List<AssImage> = rendered(11_000) ?: return@runTest

        assertTrue(images.isNotEmpty(), "the first verse rendered no images at all")
        assertTrue(
            coverage(images) > 500,
            "only ${coverage(images)} covered pixels: that is a stray mark, not styled text",
        )
    }

    @Test
    fun nothingIsDrawnBeforeTheFirstCueBegins() = runTest {
        // Two seconds in, before anything starts. A renderer answering with the
        // same images at every timestamp would satisfy the test above and be
        // completely broken.
        val images: List<AssImage> = rendered(2_000) ?: return@runTest

        assertTrue(images.isEmpty(), "${images.size} images were drawn before the first cue")
    }

    @Test
    fun theRomajiSitsAtTheTopAndTheEnglishAtTheBottom() = runTest {
        // The two styles differ only in alignment — 8 against 2 — and both are
        // on screen together at 11 seconds. Glyphs in one half only means
        // positioning was ignored and everything stacked at the default.
        val images: List<AssImage> = rendered(11_000) ?: return@runTest
        if (images.isEmpty()) return@runTest

        val top: Boolean = images.any { it.y + it.height < MID_FRAME }
        val bottom: Boolean = images.any { it.y > MID_FRAME }

        assertTrue(top, "nothing rendered in the top half, where the romaji is aligned")
        assertTrue(bottom, "nothing rendered in the bottom half, where the english is aligned")
    }

    @Test
    fun theTransformedVerseStillRendersAfterItsColourAnimationRuns() = runTest {
        // The 18.99 to 23.46 cue carries a \t colour transform. At 21 seconds it
        // is past the transform's own timestamp, which is where a renderer that
        // mishandles \t goes blank rather than wrong.
        val images: List<AssImage> = rendered(21_000) ?: return@runTest

        assertTrue(images.isNotEmpty(), "the transformed verse rendered nothing at 21s")
        assertTrue(coverage(images) > 500, "the transformed verse rendered ${coverage(images)} pixels")
    }

    @Test
    fun theTrackStillRendersWhenItsFontManifestIsMissing() = runTest {
        // A server that lost its fonts.json. A subtitle in a fallback face is
        // worse than the right one and far better than none, and the failure
        // must not be an exception on a path a viewer is waiting on.
        val images: List<AssImage> = rendered(11_000, MANIFEST_URL) ?: return@runTest

        assertTrue(images.isNotEmpty(), "a missing font manifest took the whole track down")
    }

    @Test
    fun aCueDrawsMoreThanOneRunSoTheGlyphsAreSeparate() = runTest {
        // libass emits a run per glyph group rather than one bitmap per line.
        // A single image covering the frame would mean something upstream
        // flattened them, and the positions this pipeline relies on are gone.
        val images: List<AssImage> = rendered(11_000) ?: return@runTest
        if (images.isEmpty()) return@runTest

        assertTrue(images.size > 1, "the whole cue arrived as one image")
        assertTrue(
            images.none { it.width >= FRAME_WIDTH && it.height >= FRAME_HEIGHT },
            "a run covered the entire frame, which is a flattened composite",
        )
    }
}
