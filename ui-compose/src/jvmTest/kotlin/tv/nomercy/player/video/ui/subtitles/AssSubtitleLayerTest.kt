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
import androidx.compose.ui.unit.IntSize
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
    fun aTrackIsRasterizedAtTheSurfaceRatherThanAtItsOwnResolution() = runComposeUiTest {
        // The overlay here is 640x360 and the track was authored for 1080p.
        //
        // This asserted the opposite until 2026-08-08, when Stoney reported the
        // subtitles looking "a bit low quality, like 720p instead of 1080p" —
        // which is exactly what a 1280x720-authored script rasterised at 720p
        // and stretched over a 1080p pane looks like.
        //
        // The old rule read PlayRes as a resolution. It is a COORDINATE SPACE,
        // and libass takes it separately as the storage size; the frame is what
        // it rasterises into. Measured on No-Rin's own script with
        // subtitles-libass:assGeometryProbe, storage pinned to its 1280x720
        // PlayRes and the frame varied:
        //
        //   frame  640x360  ->  extent 104,17 .. 549,356    (549/640  = 0.858)
        //   frame 1280x720  ->  extent 209,34 .. 1084,708   (1084/1280 = 0.847)
        //   frame 1920x1080 ->  extent 314,51 .. 1619,1047  (1619/1920 = 0.843)
        //
        // The same layout at every size, outline and shadow included — that
        // script sets ScaledBorderAndShadow: yes and libass scales them with the
        // storage ratio. So the frame is free to follow the surface, and glyphs
        // are outlines: rasterising them larger produces real detail rather than
        // the invented detail an upscaled bitmap gives.
        val renderer = OneRunRenderer(space = AssSize(1920, 1080))
        var mounted: Boolean by mutableStateOf(true)

        // Mounted directly rather than through the chrome. What size the
        // renderer is asked for does not involve the chrome at all, and the
        // test above already proves the slot is wired.
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

        // 640x360 pane, 16:9 track: the video's box IS the pane here, so the
        // raster is the pane — sharp, and in the right place.
        assertEquals(
            AssSize(WIDTH, HEIGHT),
            AssSize(renderer.width, renderer.height),
            "the cue was rasterised at the track's space rather than at the pixels on screen",
        )
    }

    @Test
    fun aPortraitPaneRastersTheVideosBoxAndNotTheWholeScreen() {
        // The regression this pair exists to prevent, in the direction that
        // shipped for an hour: a 16:9 script laid out over a 9:20 phone pane
        // put a positioned sign in the letterbox above the picture and the
        // dialogue below it. Photographed on Stoney's phone.
        //
        // Pure, because it is arithmetic — mounting a composable to check a
        // rectangle would only add a frame loop to wait for.
        val portrait = rasterSize(AssSize(1280, 720), IntSize(1080, 2400))

        assertEquals(1080, portrait.width, "the raster did not span the pane's width")
        assertEquals(607, portrait.height, "the raster was not the video's box: 1080 / (1280/720) = 607")
    }

    @Test
    fun theRasterFollowsThePanelHoweverBigItIs() {
        // There is no ceiling. A cap can only throttle the case where the panel
        // HAS the pixels — 4K, ultrawide, whatever comes next — and the box is
        // already bounded by the pane, so nothing needs protecting from it.
        //
        // 16:9 4K, 21:9 at 4K height, and 8K: each gets the video's box at the
        // panel's own resolution.
        assertEquals(IntSize(3840, 2160), rasterSize(AssSize(1280, 720), IntSize(3840, 2160)))
        assertEquals(IntSize(3840, 2160), rasterSize(AssSize(1920, 1080), IntSize(5120, 2160)))
        assertEquals(IntSize(7680, 4320), rasterSize(AssSize(1920, 1080), IntSize(7680, 4320)))
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
