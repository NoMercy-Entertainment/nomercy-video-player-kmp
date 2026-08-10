// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

// Whether a saved position is close enough to the end to treat as finished.
//
// Ported from the deprecated app's `VideoPlayer.resumeGuardMs` (R6: "the
// shipped Android app is a source, not a cross-check" — this row is explicitly
// "Must build", not an app-only invention). Absent from the web trio because
// the web player never had a start-kill loop to guard against: a server-saved
// progress at or near the end sent straight back into `load(startPositionMs)`
// hits end-of-stream immediately, and every subsequent play attempt repeats
// it. Guarding at load time, once, is cheaper than teaching every consumer of
// `ended` to tell "just finished" from "chose to restart" apart.
public object ResumeGuard {

    // Within this many seconds of the end, treat as fully watched.
    public const val TRAILING_SECONDS: Long = 30L

    // At or above this percentage, treat as fully watched.
    public const val PERCENT_THRESHOLD: Float = 95f

    // The `startPositionMs` [tv.nomercy.player.core.ports.LoadOptions] should
    // actually carry — `savedSeconds` unchanged (as milliseconds) short of the
    // finished thresholds, `0` once either one is crossed. Both durations are
    // seconds, matching the server's own contract; the return value is
    // milliseconds, matching the engine's.
    public fun startPositionMs(
        savedSeconds: Long,
        durationSeconds: Long,
        percentComplete: Float = (savedSeconds.toFloat() / durationSeconds.toFloat()) * 100f,
    ): Long {
        val nearEnd: Boolean = savedSeconds >= durationSeconds - TRAILING_SECONDS
        val highPercent: Boolean = percentComplete >= PERCENT_THRESHOLD
        return if (nearEnd || highPercent) 0L else savedSeconds * 1000L
    }
}
