// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssFrameCompositor
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssPipelineTest {

    // The decoder announces about 44 ms before release and a slow render (a changed
    // picture) takes longer than two frames. Each frame still needs its own render, and
    // its picture must appear at its release, never before.
    @Test
    fun slowRenderIsPipelinedAndPublishedAtRelease() = runBlocking(Dispatchers.Default) {
        val renderer = BurstRenderer()
        val source = ConflatedSource(this)
        val published: MutableList<Long> = Collections.synchronizedList(mutableListOf())
        val drawing = AssDrawing(AssFrameCompositor(), AssPictureSurface())
        val feed = AssFeed({ 0L }, source) { 1.0 }

        val job = launch { renderer.drive(drawing, IntSize(WIDTH, HEIGHT), feed) { published += System.nanoTime() } }
        source.start()
        withTimeoutOrNull(RUN_MS) { delay(Long.MAX_VALUE) }
        job.cancel()

        val missed: List<Long> = source.frameTimes.filter { it !in renderer.asked.toSet() }
        assertEquals(0, missed.size, "frames with no render at own time: ${missed.size} of $FRAMES")

        assertTrue(published.size <= FRAMES, "published ${published.size} pictures for $FRAMES frames")
        val earliest: Int = published.indices.count { i -> published[i] < source.releases[i] - EARLY_TOLERANCE_NS }
        assertEquals(0, earliest, "pictures published before their frame's release: $earliest")
        assertTrue(published.size >= FRAMES - SLOW_FRAMES, "published ${published.size} of $FRAMES")
    }
}

private const val WIDTH = 640
private const val HEIGHT = 360
private const val FRAMES = 60
private const val SLOW_FRAMES = 10
private const val START_MS = 115_000L
private const val FRAME_NS = 41_708_333L
private const val ANNOUNCE_NS = 44_000_000L
private const val EARLY_TOLERANCE_NS = 2_000_000L
private const val RUN_MS = 4_500L
private const val SOURCE_DEPTH = 32

// Every sixth render takes 100 ms, like the changed pictures on the phone.
internal class BurstRenderer : AssRenderer {
    val asked: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    override fun addFont(name: String, data: ByteArray): Unit = Unit

    override fun clearFonts(): Unit = Unit

    override fun loadTrack(assContent: String): Unit = Unit

    override fun storageSize(): AssSize? = null

    override fun storageSize(width: Int, height: Int): Unit = Unit

    override fun frameSize(width: Int, height: Int): Unit = Unit

    override fun render(timeMillis: Long): AssFrame {
        val unchanged = AssFrame(images = emptyList(), changed = false)
        if (timeMillis < START_MS || asked.lastOrNull() == timeMillis) return unchanged
        asked += timeMillis
        Thread.sleep(if (asked.size % 6 == 0) 100L else 8L)
        return AssFrame(images = emptyList(), changed = true)
    }

    override fun release(): Unit = Unit
}

// Announces like the app's source: a bounded queue that drops the oldest only when far behind.
internal class ConflatedSource(private val scope: CoroutineScope) : AssFrameSource {
    val frameTimes: List<Long> = List(FRAMES) { START_MS + (it * FRAME_NS / 1_000_000L) }
    var releases: List<Long> = emptyList()
    private val ticks = Channel<AssFrameTick>(SOURCE_DEPTH, BufferOverflow.DROP_OLDEST)

    fun start() {
        val begin: Long = System.nanoTime() + 300_000_000L
        releases = List(FRAMES) { begin + it * FRAME_NS }
        scope.launch {
            for (i in 0 until FRAMES) {
                val wait: Long = releases[i] - ANNOUNCE_NS - System.nanoTime()
                if (wait > 0) delay(wait / 1_000_000L)
                ticks.trySend(AssFrameTick(frameTimes[i], releases[i]))
            }
        }
    }

    override suspend fun next(): AssFrameTick = ticks.receive()

    override suspend fun awaitRelease(tick: AssFrameTick) {
        val wait: Long = tick.releaseNs - System.nanoTime()
        if (wait > 0) delay(wait / 1_000_000L)
    }
}
