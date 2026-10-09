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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssImage
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The viewer's text size reaching a cue that is already on screen.
 *
 * ASS was drawn exactly as authored whatever the settings menu said, and a size
 * picked while the cue sat on screen (or the video was paused) changed nothing,
 * because libass answers "not changed" for a frame it has already rasterized
 * and the layer keeps what it has on that answer.
 */
@OptIn(ExperimentalTestApi::class)
class AssFontScaleRepaintTest {

    @Test
    fun aSizePickedWhileACueIsHeldRepaintsItAtTheNewSize() = runComposeUiTest {
        val renderer = ScaledRunRenderer()
        var percent: Int by mutableIntStateOf(100)

        setContent {
            Box(modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp)) {
                AssSubtitleLayer(
                    renderer = renderer,
                    positionMs = { 0L },
                    subtitleStyle = SubtitleStyle(fontSize = percent),
                )
            }
        }

        waitUntil(timeoutMillis = TIMEOUT_MS) { renderer.renders > 0 }
        waitUntil(timeoutMillis = TIMEOUT_MS) { renderer.renders > 1 && !paintedAtWideEdge() }

        percent = 200

        val repainted: Boolean = runCatching {
            waitUntil(timeoutMillis = TIMEOUT_MS) { paintedAtWideEdge() }
        }.isSuccess

        percent = 100
        waitForIdle()

        assertEquals(DOUBLE, renderer.scales.lastOrNull(), "the renderer was never told the viewer's size")
        assertTrue(repainted, "the held cue was not repainted after the size changed")
    }

    private fun ComposeUiTest.paintedAtWideEdge(): Boolean =
        onNodeWithTag(ASS_SUBTITLE_TAG).captureToImage().toPixelMap()[WIDE_EDGE_X, RUN_Y + 1].alpha > 0f
}

// One white run whose width follows the font scale, and "changed" only on the
// first render: what libass answers for a cue it has already drawn.
private class ScaledRunRenderer : AssRenderer {
    val scales: MutableList<Double> = mutableListOf()

    @Volatile
    var renders: Int = 0
        private set

    @Volatile
    private var scale: Double = 1.0

    override fun addFont(name: String, data: ByteArray): Unit = Unit

    override fun clearFonts(): Unit = Unit

    override fun loadTrack(assContent: String): Unit = Unit

    override fun storageSize(): AssSize? = null

    override fun storageSize(width: Int, height: Int): Unit = Unit

    override fun frameSize(width: Int, height: Int): Unit = Unit

    override fun fontScale(scale: Double) {
        synchronized(scales) { scales += scale }
        this.scale = scale
    }

    override fun render(timeMillis: Long): AssFrame {
        val first: Boolean = renders == 0
        renders += 1
        return AssFrame(images = listOf(run()), changed = first)
    }

    override fun release(): Unit = Unit

    private fun run(): AssImage {
        val width: Int = (RUN_WIDTH * scale).toInt()
        return AssImage(
            RUN_X,
            RUN_Y,
            width,
            RUN_HEIGHT,
            width,
            OPAQUE_WHITE,
            ByteArray(width * RUN_HEIGHT) { FULL_COVERAGE },
        )
    }
}

private const val WIDTH = 640

private const val HEIGHT = 360

private const val RUN_X = 40

private const val RUN_Y = 40

private const val RUN_WIDTH = 80

private const val RUN_HEIGHT = 40

// Past the 80-wide run at 100%, inside the 160-wide run at 200%.
private const val WIDE_EDGE_X = RUN_X + 120

private const val DOUBLE = 2.0

private const val OPAQUE_WHITE = 0xFFFFFF00.toInt()

private const val FULL_COVERAGE = 0xFF.toByte()

private const val TIMEOUT_MS = 5_000L
