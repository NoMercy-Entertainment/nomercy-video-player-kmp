// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

/**
 * A playhead that moves every frame, from one that only moves when the engine
 * says so.
 *
 * In a browser `video.currentTime` is exact whenever it is read, so a subtitle
 * overlay can ask per frame and get a per-frame answer. libVLC does not work
 * that way: it raises `timeChanged` on its own cadence — a few times a second —
 * and the position between two of those events is the same number read
 * repeatedly. Rendering a moving subtitle against it produces a cue that jumps
 * several times a second no matter how often the frame is drawn, which is what
 * a karaoke wipe stepping instead of sliding actually is.
 *
 * So the reported position is a fixed point and the time since it arrived is
 * added on. Nothing here predicts the future: it reports how far the media has
 * run since the last thing the engine said, which is what a clock does between
 * ticks.
 *
 * Extrapolation is BOUNDED, and that bound is what makes this safe when
 * playback is not advancing. A paused player reports the same position for as
 * long as it is paused, and a version of this without a limit would walk the
 * subtitles off into a part of the film nobody is watching. The limit tracks
 * the cadence actually observed rather than a constant, because that cadence
 * differs per backend and per file.
 *
 * Not thread-safe. One of these belongs to one subtitle layer and is read from
 * its own loop.
 */
internal class SmoothedPlayhead(
    private val maximumDriftMs: Long = DEFAULT_MAXIMUM_DRIFT_MS,
) {

    private var reportedMs: Long = 0
    private var reportedAtNanos: Long = 0
    private var intervalMs: Long = 0
    private var seeded: Boolean = false
    private var lastAnswerMs: Long = 0

    /**
     * The position to draw at, given what the engine last reported and when.
     *
     * [nowNanos] is a monotonic reading — the frame clock — never a wall clock,
     * because a wall clock can step backwards and a subtitle that steps
     * backwards re-renders a cue that already left.
     */
    fun positionAt(reported: Long, nowNanos: Long): Long {
        if (!seeded) {
            seeded = true
            reportedMs = reported
            reportedAtNanos = nowNanos
            lastAnswerMs = reported
            return reported
        }

        // A new report re-anchors, and the gap between reports is the cadence
        // this backend actually has.
        if (reported != reportedMs) {
            // Backwards is a seek. Everything learned about the cadence still
            // holds; the anchor does not.
            val elapsedMs: Long = (nowNanos - reportedAtNanos) / NANOS_PER_MILLI
            if (reported > reportedMs && elapsedMs > 0) {
                intervalMs = if (intervalMs == 0L) elapsedMs else (intervalMs + elapsedMs) / 2
            }
            reportedMs = reported
            reportedAtNanos = nowNanos
            lastAnswerMs = reported
            return reported
        }

        val sinceReportMs: Long = (nowNanos - reportedAtNanos) / NANOS_PER_MILLI
        if (sinceReportMs <= 0) return lastAnswerMs

        // One cadence of headroom, so a position is carried to about where the
        // next report is due and no further. Beyond that the engine is not
        // advancing — paused, stalled, or ended — and standing still is the
        // honest answer.
        val allowance: Long = minOf(
            maximumDriftMs,
            if (intervalMs > 0) intervalMs + intervalMs / 2 else maximumDriftMs,
        )
        val answer: Long = reportedMs + minOf(sinceReportMs, allowance)

        // Never backwards. Between a shrinking allowance and a late frame the
        // arithmetic can produce a smaller number than last time, and a cue
        // that goes back re-runs a wipe the viewer already watched.
        if (answer < lastAnswerMs) return lastAnswerMs
        lastAnswerMs = answer
        return answer
    }
}

// Half a second is longer than any backend's reporting gap seen here and short
// enough that a pause is not visibly overrun before the allowance closes it.
private const val DEFAULT_MAXIMUM_DRIFT_MS = 500L
private const val NANOS_PER_MILLI = 1_000_000L
