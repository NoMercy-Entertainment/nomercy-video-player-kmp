// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

// How far a run of double-taps has moved, as the indicator shows it.
//
// Cumulative rather than one number per tap. Somebody skipping an opening taps
// four times in a second, and four separate "10 seconds" flashes tell them
// nothing about where they have got to — what they want to read is forty.
//
// The run ends when they stop, and a tap on the other side starts a new one
// rather than subtracting from this. Two directions in one figure is a number
// that goes down while the film goes forward.
public data class SeekRun(
    val side: SeekSide? = null,
    val seconds: Float = 0f,
    val lastTapMs: Long = 0,
) {

    public val isEmpty: Boolean get() = side == null

    // What the indicator reads. Signed, because the sign is the whole message on
    // a control with no other label.
    public val label: String
        get() = when (side) {
            SeekSide.Forward -> "+${seconds.toInt()}s"
            SeekSide.Back -> "-${seconds.toInt()}s"
            null -> ""
        }
}

public enum class SeekSide { Back, Forward }

// One double-tap, folded into whatever run is in progress.
//
// A tap after the window has passed starts a new run, and so does a tap on the
// other side. Both are the same rule: the figure describes one continuous
// gesture, and anything else is a different gesture.
public fun SeekRun.plus(side: SeekSide, seconds: Float, nowMs: Long, windowMs: Long): SeekRun {
    val continues: Boolean = this.side == side && nowMs - lastTapMs < windowMs

    return SeekRun(
        side = side,
        seconds = if (continues) this.seconds + seconds else seconds,
        lastTapMs = nowMs,
    )
}

// Whether the indicator should still be on screen.
//
// Asked rather than scheduled, so the run has no timer of its own and nothing to
// dispose. Whatever is drawing it already recomposes on a clock.
public fun SeekRun.isVisible(nowMs: Long, windowMs: Long): Boolean =
    !isEmpty && nowMs - lastTapMs < windowMs
