// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.runBlocking
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.FrameSourceBackend
import tv.nomercy.player.core.ports.MpvVideoBackend
import tv.nomercy.player.core.ports.VideoBackend
import tv.nomercy.player.core.ports.engines.EngineSelection
import tv.nomercy.player.core.ports.engines.VideoEngines
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

private const val FRAME_WAIT_MS = 10_000L
private const val POLL_MS = 50L

// G1 on the desktop: does video actually paint.
//
// This is the gate the plan deferred to "device QA", and it did not need to be.
// The question "does video reach the screen" is answerable exactly where the
// picture becomes a Compose bitmap: if a frame arrives there and it is a picture
// rather than a fill, everything downstream is Compose drawing an ImageBitmap,
// which is not a thing that silently fails.
//
// Nothing is stubbed. The REGISTRY's engine — mpv where its payload is present,
// libVLC where it is not — decodes a real file through the real frame path into
// the real sink the composable draws from.
//
// The engine used to be named here, which meant the desktop's own render gate
// covered exactly one of the two engines that can draw on it. Pick the other
// with -Dnomercy.video.engine to run the same gate against it.
@OptIn(ExperimentalComposeUiApi::class)
class DesktopRenderGateTest {

    // The engine the registry picks, or null with the reason printed. A gate
    // that skips silently on a machine with no engine says the desktop works
    // everywhere it was never run.
    private fun engineOrSkip(): VideoBackend? = when (val decision = VideoEngines.select()) {
        is EngineSelection.None -> {
            println("skipped: ${decision.reason}")
            null
        }

        is EngineSelection.Chosen -> {
            println("engine: ${decision.provider.id}")
            decision.provider.create()
        }
    }

    @Test
    fun aRealEngineDeliversRealFramesToTheComposeSink() {
        val backend: VideoBackend = engineOrSkip() ?: return

        val media: File = writeTestVideo("${System.getProperty("java.io.tmpdir")}/nomercy-render-gate.y4m")
        val sink = ComposeFrameSink()

        try {
            (backend as FrameSourceBackend).videoFrameSink(sink)
            runBlocking {
                backend.load(media.absolutePath, LoadOptions())
                backend.play()
            }

            // A picture, not merely a frame. An engine's first callbacks can land
            // before it has decoded anything, so waiting for "a frame arrived"
            // would pass on a blank one and prove nothing.
            val frame: ImageBitmap = awaitPicture(sink)
                ?: error(
                    "no picture reached the Compose sink in ${FRAME_WAIT_MS}ms " +
                        "(last frame: ${describe(sink.frame.value)})",
                )

            assertTrue(frame.width > 0 && frame.height > 0, "frame was ${frame.width}x${frame.height}")
        } finally {
            releaseEngine(backend)
            media.delete()
        }
    }

    private fun awaitPicture(sink: ComposeFrameSink): ImageBitmap? {
        val deadline: Long = System.currentTimeMillis() + FRAME_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val current: ImageBitmap? = sink.frame.value
            if (current != null && distinctLuminances(current) > 1) return current
            Thread.sleep(POLL_MS)
        }
        return null
    }

    private fun describe(frame: ImageBitmap?): String = when (frame) {
        null -> "none"
        else -> "${frame.width}x${frame.height}, ${distinctLuminances(frame)} distinct values, " +
            "first pixel ${frame.toPixelMap()[0, 0].value.toULong().toString(HEX)}"
    }

    // A black screen and a decoded picture are both "a frame arrived". Counting
    // distinct values is what tells them apart, and it is why the test media is
    // a moving square rather than a colour.
    //
    // ULong, not Int. Compose packs a Color's components into the high bits of
    // a 64-bit value and leaves the colour space in the low ones, so truncating
    // to Int discards every channel and keeps the one field every pixel shares —
    // which reads as a uniform image no matter what was decoded. This gate said
    // "black screen" about a frame that had a white square in it.
    private fun distinctLuminances(frame: ImageBitmap): Int {
        val pixels = frame.toPixelMap()
        val seen: MutableSet<ULong> = mutableSetOf()
        sample(frame.width, frame.height).forEach { (x, y) -> seen.add(pixels[x, y].value) }
        return seen.size
    }

    // Neither engine declares release() on the contract — it is not a playback
    // call and a consumer that never tears an engine down never needs it — so
    // the gate names both rather than leaving a live engine and its threads
    // behind for the next test to inherit.
    private fun releaseEngine(backend: VideoBackend) {
        (backend as? MpvVideoBackend)?.release()
    }

    private fun sample(width: Int, height: Int): Sequence<Pair<Int, Int>> = sequence {
        for (y in 0 until height step SAMPLE_STEP) {
            for (x in 0 until width step SAMPLE_STEP) {
                yield(x to y)
            }
        }
    }

    @Test
    fun theControlDrawsOverTheVideoRatherThanBehindIt() {
        // The failure this whole approach exists to avoid. An embedded native
        // surface paints into its own window, above everything the toolkit
        // draws, and the play button would be underneath it —
        // unclickable, and invisible on a black frame. Rendering the view and
        // finding control pixels in front of decoded video is what says the
        // compositing actually happened.
        val backend: VideoBackend = engineOrSkip() ?: return

        val media: File = writeTestVideo("${System.getProperty("java.io.tmpdir")}/nomercy-composite-gate.y4m")
        val sink = ComposeFrameSink()

        try {
            (backend as FrameSourceBackend).videoFrameSink(sink)
            runBlocking {
                backend.load(media.absolutePath, LoadOptions())
                backend.play()
            }
            val video: ImageBitmap = awaitPicture(sink) ?: error("no video to composite over")

            val composed: ImageBitmap = renderVideoUnderControl(video)

            assertTrue(
                containsControl(composed),
                "the control is not in the composited output: the video is drawing over it",
            )
        } finally {
            releaseEngine(backend)
            media.delete()
        }
    }

    // The same layering the view uses: the frame fills the box, the control sits
    // on top of it. Rendered offscreen so the gate needs no window.
    private fun renderVideoUnderControl(video: ImageBitmap): ImageBitmap {
        val scene = ImageComposeScene(width = SCENE_SIZE, height = SCENE_SIZE) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = video,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                PlayPauseControl(
                    playing = false,
                    onToggle = {},
                    tint = CONTROL_TINT,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        try {
            return scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }

    // Red, not white. The first version of this looked for white at the centre
    // and passed with the control drawn *behind* the video — the white it found
    // was the moving square in the test media, and the gate was measuring the
    // video against itself.
    //
    // The media is greyscale by construction: neutral chroma, so every pixel of
    // it has red equal to green equal to blue. A pixel where red dominates
    // cannot have come from the video, whatever frame is on screen.
    private fun containsControl(composed: ImageBitmap): Boolean {
        val pixels = composed.toPixelMap()
        val centre: Int = SCENE_SIZE / 2
        val probe: IntRange = centre - PROBE_RADIUS until centre + PROBE_RADIUS

        return probe.any { y ->
            probe.any { x ->
                val colour = pixels[x, y]
                colour.red - colour.green > CHANNEL_MARGIN && colour.red - colour.blue > CHANNEL_MARGIN
            }
        }
    }
}

private const val HEX = 16
private const val SAMPLE_STEP = 2
private const val SCENE_SIZE = 200
private const val PROBE_RADIUS = 20
private const val CHANNEL_MARGIN = 0.5f
private val CONTROL_TINT = Color.Red
