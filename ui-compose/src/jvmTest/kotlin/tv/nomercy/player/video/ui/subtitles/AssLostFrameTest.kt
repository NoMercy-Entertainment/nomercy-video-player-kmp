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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssImage
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AssLostFrameTest {

    // libass reports each change once. A frame rendered while the loop restarts
    // (a resize, the next episode) must still reach the screen, or the old cue stays.
    @Test
    fun aCueThatEndsWhileTheLoopRestartsLeavesTheScreen() = runComposeUiTest {
        val renderer = EndingCueRenderer()
        var height: Int by mutableIntStateOf(HEIGHT)
        var mounted: Boolean by mutableStateOf(true)

        setContent {
            if (mounted) {
                Box(modifier = Modifier.width(WIDTH.dp).height(height.dp)) {
                    AssSubtitleLayer(renderer = renderer, positionMs = { 0L })
                }
            }
        }

        fun center() = onNodeWithTag(ASS_SUBTITLE_TAG).captureToImage().toPixelMap()[CENTER_X, CENTER_Y]
        waitUntil(timeoutMillis = TIMEOUT_MS) { center().alpha == 1f }

        renderer.end()
        waitUntil(timeoutMillis = TIMEOUT_MS) { renderer.holding }
        height = HEIGHT - RESIZE
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        renderer.release.countDown()

        try {
            waitUntil(timeoutMillis = TIMEOUT_MS) { center().alpha == 0f }
        } finally {
            mounted = false
        }
    }
}

// One cue on screen, then the frame that ends it held until the test lets go.
// After that, nothing changes: what libass says once it has reported the end.
private class EndingCueRenderer : AssRenderer {
    val release = CountDownLatch(1)

    @Volatile var holding: Boolean = false
        private set

    @Volatile private var ended: Boolean = false
    private var shown: Boolean = false
    private var reported: Boolean = false

    fun end() {
        ended = true
    }

    override fun render(timeMillis: Long): AssFrame {
        if (!ended) {
            val first: Boolean = !shown
            shown = true
            return AssFrame(images = listOf(cue()), changed = first)
        }
        if (reported) return AssFrame(images = emptyList(), changed = false)

        holding = true
        release.await(HOLD_SECONDS, TimeUnit.SECONDS)
        reported = true
        return AssFrame(images = emptyList(), changed = true)
    }

    override fun addFont(name: String, data: ByteArray): Unit = Unit

    override fun clearFonts(): Unit = Unit

    override fun loadTrack(assContent: String): Unit = Unit

    override fun storageSize(): AssSize? = null

    override fun storageSize(width: Int, height: Int): Unit = Unit

    override fun frameSize(width: Int, height: Int): Unit = Unit

    override fun release(): Unit = Unit

    // Positional: x, y, width, height, stride, the libass color, the coverage.
    private fun cue(): AssImage = AssImage(
        RUN_X,
        RUN_Y,
        RUN_WIDTH,
        RUN_HEIGHT,
        RUN_WIDTH,
        OPAQUE_WHITE,
        ByteArray(RUN_WIDTH * RUN_HEIGHT) { FULL_COVERAGE },
    )
}

private const val WIDTH = 640
private const val HEIGHT = 360
private const val RESIZE = 10
private const val RUN_X = 40
private const val RUN_Y = 40
private const val RUN_WIDTH = 80
private const val RUN_HEIGHT = 40
private const val CENTER_X = RUN_X + RUN_WIDTH / 2
private const val CENTER_Y = RUN_Y + RUN_HEIGHT / 2
private const val OPAQUE_WHITE = 0xFFFFFF00.toInt()
private const val FULL_COVERAGE = 0xFF.toByte()
private const val TIMEOUT_MS = 5_000L
private const val HOLD_SECONDS = 10L
private const val SETTLE_FRAMES = 5
