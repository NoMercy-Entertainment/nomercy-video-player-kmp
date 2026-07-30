// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import java.util.concurrent.atomic.AtomicLong

/**
 * How many frames arrive and how many get painted, per second, on demand.
 *
 * The desktop picture was a slideshow and every explanation for it was
 * plausible: the conversion, the recomposition, the vsync, the callback thread.
 * Guessing at four candidates from a screenshot is how a fix lands one layer
 * below the fault, so this counts both ends of the pipe separately. A delivery
 * rate that matches the clip and a paint rate that does not means the fault is
 * in Compose; the two dropping together means it is upstream of the sink.
 *
 * The size is here for the same reason. An adaptive source renegotiates it
 * mid-play, and a per-frame cost that quadruples nine seconds in reads as a leak
 * until you can see that the picture got twice as wide.
 *
 * Off unless asked for, because a println per second from a library is noise in
 * every application that did not want it. Enable with either
 * `-Dnomercy.player.frameStats=true` on the app's JVM or
 * `NOMERCY_PLAYER_FRAME_STATS=1` in its environment — the second one exists
 * because a Gradle `run` task forwards the environment and not the properties.
 */
internal object FrameStats {

    internal val enabled: Boolean =
        System.getProperty(PROPERTY) != null || System.getenv(VARIABLE) != null

    private val delivered: AtomicLong = AtomicLong()
    private val painted: AtomicLong = AtomicLong()
    private val copyNanos: AtomicLong = AtomicLong()
    private val installNanos: AtomicLong = AtomicLong()
    private val lastFrameNumber: AtomicLong = AtomicLong()
    private val width: AtomicLong = AtomicLong()
    private val height: AtomicLong = AtomicLong()
    private val windowOpenedAt: AtomicLong = AtomicLong(System.nanoTime())

    /** The size libVLC negotiated, which an adaptive source changes mid-play. */
    fun format(sourceWidth: Int, sourceHeight: Int) {
        if (!enabled) return
        width.set(sourceWidth.toLong())
        height.set(sourceHeight.toLong())
    }

    /**
     * One frame handed over by libVLC, split by where its time went: reading it
     * out of libVLC's buffer, and handing it to Skia.
     */
    fun delivered(readNanos: Long, handOverNanos: Long) {
        if (!enabled) return
        delivered.incrementAndGet()
        copyNanos.addAndGet(readNanos)
        installNanos.addAndGet(handOverNanos)
        reportIfWindowClosed()
    }

    /**
     * One draw pass over the video node. Not a recomposition — a paint.
     *
     * Takes the frame number it painted so the caller has to READ the counter to
     * call this, which is what subscribes the draw phase to it. That the number
     * is only used when the stats are on is deliberate: the subscription is the
     * work, and it happens at the call site either way.
     */
    fun painted(frameNumber: Int) {
        if (!enabled) return
        painted.incrementAndGet()
        lastFrameNumber.set(frameNumber.toLong())
        reportIfWindowClosed()
    }

    // Whoever crosses the second boundary first prints and resets. The
    // compare-and-set is what keeps the vlcj thread and the Compose render
    // thread from both claiming the same window and halving each other's counts.
    private fun reportIfWindowClosed() {
        val now: Long = System.nanoTime()
        val opened: Long = windowOpenedAt.get()
        val elapsed: Long = now - opened
        if (elapsed < WINDOW_NANOS) return
        if (!windowOpenedAt.compareAndSet(opened, now)) return

        val frames: Long = delivered.getAndSet(0)
        val paints: Long = painted.getAndSet(0)
        val seconds: Double = elapsed.toDouble() / NANOS_PER_SECOND

        println(
            "frame-stats" +
                " ${width.get()}x${height.get()}" +
                " delivered=" + rate(frames, seconds) +
                "/s drawn=" + rate(paints, seconds) +
                "/s read=" + millis(copyNanos.getAndSet(0), frames) +
                "ms handover=" + millis(installNanos.getAndSet(0), frames) +
                "ms painted-frame=" + lastFrameNumber.get(),
        )
    }

    private fun rate(count: Long, seconds: Double): String =
        ((count / seconds) * TENTHS).toLong().let { "${it / TENTHS}.${it % TENTHS}" }

    private fun millis(nanos: Long, frames: Long): String {
        if (frames == 0L) return "0.00"
        val hundredths: Long = nanos / frames * HUNDREDTHS / NANOS_PER_MILLI
        return "${hundredths / HUNDREDTHS}.${hundredths % HUNDREDTHS}"
    }
}

private const val PROPERTY: String = "nomercy.player.frameStats"
private const val VARIABLE: String = "NOMERCY_PLAYER_FRAME_STATS"
private const val NANOS_PER_SECOND: Double = 1_000_000_000.0
private const val NANOS_PER_MILLI: Long = 1_000_000
private const val WINDOW_NANOS: Long = 1_000_000_000
private const val TENTHS: Long = 10
private const val HUNDREDTHS: Long = 100
