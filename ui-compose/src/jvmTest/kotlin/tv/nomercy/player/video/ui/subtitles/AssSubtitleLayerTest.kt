// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssImage
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import tv.nomercy.player.video.ui.chrome.ChromeSlots
import tv.nomercy.player.video.ui.chrome.RecordingVideoBackend
import tv.nomercy.player.video.ui.chrome.VideoChrome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The styled cues, on screen.
 *
 * Graded through the whole chrome rather than by calling the layer directly,
 * because what was wrong was never the layer: libass rasterized correct frames
 * the whole time and no composable drew them, so the renderer, the font cache
 * and the scheduler all worked and the viewer saw nothing. A test that mounted
 * the layer by hand would have passed on the unwired build.
 *
 * Pixels rather than "the node exists", for the same reason: a node with no
 * height and a node drawing a subtitle are the same assertion to a finder.
 */
@OptIn(ExperimentalTestApi::class)
class AssSubtitleLayerTest {

    @Test
    fun aRasterizedRunIsPaintedOverThePicture() = runComposeUiTest {
        val renderer = OneRunRenderer()
        // Unmounted before the test ends, because the layer redraws on the
        // frame clock and a composition that never stops is a coroutine the
        // harness waits a full minute for before failing the test it already
        // passed.
        var mounted: Boolean by mutableStateOf(true)

        setContent {
            if (mounted) {
                val player = NMVideoPlayer(RecordingVideoBackend())
                LaunchedEffect(player) { player.setup(PlayerConfig()) }

                Box(modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp)) {
                    VideoChrome(
                        player,
                        FormFactor.Desktop,
                        slots = ChromeSlots(
                            styledSubtitles = { _, _ ->
                                AssSubtitleLayer(renderer = renderer, positionMs = { 0L })
                            },
                        ),
                    )
                }
            }
        }

        // No waitForIdle here. The layer redraws on the frame clock and never
        // goes idle by design, so waiting for that is waiting for the harness's
        // one-minute timeout. The wait above is the real condition: the frame
        // has been rasterized, and captureToImage draws what is composed.
        waitUntil(timeoutMillis = TIMEOUT_MS) { renderer.asked }

        val painted = onNodeWithTag(ASS_SUBTITLE_TAG).captureToImage().toPixelMap()
        mounted = false
        waitForIdle()

        val inside = painted[RUN_X + RUN_WIDTH / 2, RUN_Y + RUN_HEIGHT / 2]

        assertTrue(inside.alpha > 0f, "the run libass rasterized reached the surface")
        assertTrue(inside.red > HALF && inside.green > HALF, "and it kept the colour libass gave it")

        // The layer has to be transparent everywhere the run is not, or the
        // assertion above would also pass for a white rectangle covering the
        // whole picture — which is what a wrong stride or a wrong colour
        // unpacking produces, and it hides the film rather than captioning it.
        val outside = painted[RUN_X + RUN_WIDTH + MARGIN, RUN_Y + RUN_HEIGHT + MARGIN]
        assertTrue(outside.alpha == 0f, "and it covered nothing else")
    }

    @Test
    fun aTrackIsRasterizedAtItsOwnResolutionRatherThanTheOverlays() = runComposeUiTest {
        // The overlay here is 640x360 and the track was authored for 1080p.
        // Rasterizing at the overlay's size keeps the glyphs proportional and
        // not the outline, shadow or blur around them, so every cue in a
        // windowed player comes out thick and blobby.
        val renderer = OneRunRenderer(space = AssSize(1920, 1080))
        var mounted: Boolean by mutableStateOf(true)

        // Mounted directly rather than through the chrome. What size the
        // renderer is asked for does not involve the chrome at all, and the
        // test above already proves the slot is wired; carrying a whole player
        // into this one only adds coroutines for its frame loop to compete
        // with.
        setContent {
            if (mounted) {
                Box(modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp)) {
                    AssSubtitleLayer(renderer = renderer, positionMs = { 0L })
                }
            }
        }

        waitUntil(timeoutMillis = TIMEOUT_MS) { renderer.asked }
        mounted = false
        waitForIdle()

        assertEquals(
            AssSize(1920, 1080),
            AssSize(renderer.width, renderer.height),
            "the track's own space was ignored and the cue was rasterized at the overlay's size",
        )
    }
}

// One opaque white run, once. Everything after it is null, which is what the
// real renderer answers for a frame that has not changed.
private class OneRunRenderer(private var space: AssSize? = null) : AssRenderer {
    var asked: Boolean = false
        private set

    var width: Int = 0
        private set

    var height: Int = 0
        private set

    private var drawn: Boolean = false

    override fun addFont(name: String, data: ByteArray): Unit = Unit

    override fun clearFonts(): Unit = Unit

    override fun loadTrack(assContent: String): Unit = Unit

    override fun storageSize(): AssSize? = space

    override fun storageSize(width: Int, height: Int) {
        space = AssSize(width, height)
    }

    override fun frameSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    override fun render(timeMillis: Long): AssFrame? {
        asked = true
        if (drawn) return null

        drawn = true
        return AssFrame(images = listOf(run()), changed = true)
    }

    override fun release(): Unit = Unit

    // Full coverage across the whole rectangle, and libass's own colour
    // packing: 0xRRGGBBAA with an INVERSE alpha byte, so 0x00 is opaque.
    private fun run(): AssImage = AssImage(
        x = RUN_X,
        y = RUN_Y,
        width = RUN_WIDTH,
        height = RUN_HEIGHT,
        stride = RUN_WIDTH,
        colour = OPAQUE_WHITE,
        pixels = ByteArray(RUN_WIDTH * RUN_HEIGHT) { FULL_COVERAGE },
    )
}

private const val WIDTH = 640

private const val HEIGHT = 360

private const val RUN_X = 40

private const val RUN_Y = 40

private const val RUN_WIDTH = 80

private const val RUN_HEIGHT = 40

private const val OPAQUE_WHITE = 0xFFFFFF00.toInt()

private const val FULL_COVERAGE = 0xFF.toByte()

private const val HALF = 0.5f

private const val TIMEOUT_MS = 5_000L

private const val MARGIN = 20
