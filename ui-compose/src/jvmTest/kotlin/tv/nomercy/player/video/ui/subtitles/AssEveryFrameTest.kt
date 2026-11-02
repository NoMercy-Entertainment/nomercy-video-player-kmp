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
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AssEveryFrameTest {

    // 24000/1001 fps video whose renders take longer than a poll: every frame
    // must still be rendered for its own time.
    @Test
    fun everyVideoFrameGetsARenderAtItsOwnTime() = runComposeUiTest {
        val video = FakeVideo()
        val renderer = RecordingRenderer()

        setContent {
            Box(modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp)) {
                AssSubtitleLayer(renderer = renderer, positionMs = video::shownMs, frames = video.source())
            }
        }
        mainClock.autoAdvance = false
        video.start()
        while (!video.finished()) {
            Thread.sleep(VSYNC_MS)
            mainClock.advanceTimeBy(VSYNC_MS)
        }

        val rendered: Set<Long> = renderer.asked.toSet()
        val missed: List<Long> = video.framesMs.filter { it !in rendered }
        assertTrue(missed.isEmpty(), "frames with no render at own time: ${missed.size} of ${video.framesMs.size}")
    }
}

internal class FakeVideo {
    val framesMs: List<Long> = List(FRAMES) { START_MS + (it * FRAME_NS / NS_PER_MS) }
    private var startedNs: Long = 0L

    fun start() {
        startedNs = System.nanoTime() + LEAD_NS
    }

    fun releaseNs(index: Int): Long = startedNs + index * FRAME_NS

    fun finished(): Boolean = startedNs != 0L && System.nanoTime() > releaseNs(FRAMES) + LEAD_NS

    // Announces frame i ANNOUNCE_NS before its release, like the decoder's frame listener.
    fun source(): AssFrameSource = object : AssFrameSource {
        private var index = 0

        override suspend fun next(): AssFrameTick {
            val i: Int = index
            if (i >= FRAMES) withContext(Dispatchers.Default) { delay(Long.MAX_VALUE) }
            val waitNs: Long = releaseNs(i) - ANNOUNCE_NS - System.nanoTime()
            withContext(Dispatchers.Default) { if (waitNs > 0) delay(waitNs / NS_PER_MS) }
            index += 1
            return AssFrameTick(framesMs[i], releaseNs(i))
        }

        override suspend fun awaitRelease(tick: AssFrameTick) {
            val waitNs: Long = tick.releaseNs - System.nanoTime()
            withContext(Dispatchers.Default) { if (waitNs > 0) delay(waitNs / NS_PER_MS) }
        }
    }

    fun shownMs(): Long {
        val now: Long = System.nanoTime()
        val index: Int = ((now - startedNs) / FRAME_NS).toInt().coerceIn(0, FRAMES - 1)
        return framesMs[index]
    }
}

internal class RecordingRenderer : AssRenderer {
    val asked: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    override fun addFont(name: String, data: ByteArray): Unit = Unit

    override fun clearFonts(): Unit = Unit

    override fun loadTrack(assContent: String): Unit = Unit

    override fun storageSize(): AssSize? = null

    override fun storageSize(width: Int, height: Int): Unit = Unit

    override fun frameSize(width: Int, height: Int): Unit = Unit

    override fun render(timeMillis: Long): AssFrame {
        asked += timeMillis
        Thread.sleep(RENDER_MS)
        return AssFrame(images = emptyList(), changed = false)
    }

    override fun release(): Unit = Unit
}

private const val WIDTH = 640
private const val HEIGHT = 360
private const val VSYNC_MS = 16L
private const val FRAMES = 60
private const val START_MS = 115_000L
private const val FRAME_NS = 41_708_333L
private const val NS_PER_MS = 1_000_000L
private const val LEAD_NS = 500_000_000L
private const val ANNOUNCE_NS = 44_000_000L
// Longer than a poll, short enough that a loaded CI runner still keeps up.
private const val RENDER_MS = 30L
