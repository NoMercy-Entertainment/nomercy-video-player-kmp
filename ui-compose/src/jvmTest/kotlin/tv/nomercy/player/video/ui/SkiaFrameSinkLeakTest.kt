// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How many decoded frames are still alive after a long playback.
 *
 * The sink allocates a Skia bitmap per frame and installPixels copies the whole
 * picture into a native pixel ref — eight megabytes for 1080p, thirty-three for
 * 4K. Skia frees that when the Java wrapper is collected, and the wrapper is a
 * few dozen bytes, so the heap never grows enough for a collection to be worth
 * running. Stoney's machine reached twenty gigabytes and froze.
 *
 * Counted rather than weighed. Measuring bytes here would measure the
 * collector's mood: it is allowed to free nothing at all and still be correct,
 * so a byte threshold either fails on a fast machine or passes on a leak. What
 * the sink OWES is a bounded number of undead bitmaps, and that is a number this
 * can assert exactly.
 */
class SkiaFrameSinkLeakTest {

    private val width = 32
    private val height = 32

    private fun frame(): ByteBuffer =
        ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(width * height) {
                put(0); put(0); put(0); put(0xFF.toByte())
            }
            rewind()
        }

    @Test
    fun aThousandFramesLeaveAtMostAHandfulOfBitmapsAlive() {
        val sink = SkiaFrameSink()
        sink.format(width, height)

        // Drawing after each frame, because that is what the canvas does and
        // the sink releases nothing until it is told. A run where nothing is
        // ever painted is a window nobody is looking at.
        repeat(FRAMES) {
            sink.display(frame())
            sink.drawn(sink.version.intValue)
        }

        // One: the frame the canvas last painted, which it may paint again.
        // Anything above that is a picture nobody will draw and nobody will
        // free — eight megabytes each at 1080p, thirty-three at 4K.
        assertTrue(
            sink.liveBitmaps() <= CEILING,
            "after $FRAMES frames the sink still holds ${sink.liveBitmaps()} bitmaps",
        )
    }

    // A new item must not leave the previous film's picture resident either.
    @Test
    fun clearingReleasesWhatTheLastItemDecoded() {
        val sink = SkiaFrameSink()
        sink.format(width, height)
        repeat(10) {
            sink.display(frame())
            sink.drawn(sink.version.intValue)
        }

        sink.clear()

        assertTrue(sink.liveBitmaps() <= 1, "clear left ${sink.liveBitmaps()} bitmaps alive")
    }

    /**
     * Publishing and draining at the same time, from two threads, because that
     * is the only arrangement in which this has ever failed.
     *
     * The engine renders on its own loop and the canvas draws on the UI thread.
     * With a plain list between them the drain threw ConcurrentModification out
     * of Itr.remove on its first pass, every pass, into a thread nothing watches
     * — so nothing was ever released, memory climbed at eight megabytes a frame,
     * the picture looked perfect and the single-threaded tests above stayed
     * green. They could not have gone red: they have one thread.
     */
    @Test
    fun publishingAndDrawingAtOnceStillReleases() {
        val sink = SkiaFrameSink()
        sink.format(width, height)

        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)

        val canvas = Thread {
            try {
                while (!stop.get()) sink.drawn(sink.version.intValue)
            } catch (thrown: Throwable) {
                failure.set(thrown)
            }
        }

        canvas.start()
        try {
            repeat(FRAMES) { sink.display(frame()) }
        } finally {
            stop.set(true)
            canvas.join()
        }

        assertNull(failure.get(), "the drain threw: ${failure.get()}")

        // Drained once more from this thread, because the canvas may have
        // stopped mid-run; what matters is that the pile is not the whole film.
        sink.drawn(sink.version.intValue)
        assertTrue(
            sink.liveBitmaps() <= CEILING,
            "after $FRAMES concurrent frames the sink holds ${sink.liveBitmaps()} bitmaps",
        )
    }

    private companion object {
        const val FRAMES = 1000
        const val CEILING = 2
    }
}
