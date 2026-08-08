// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.format.formatSeconds

// The right-hand clock, which has THREE answers and drew one.
//
// `_formatRemaining` in the web chrome:
//
//     if (dur <= 0) return formatSeconds(0);
//     if (this._showRemaining) return `-${formatSeconds(Math.max(0, dur - cur))}`;
//     return formatSeconds(dur);
//
// The unknown-duration case is the one that shows. Every live stream reports a
// duration of nought, and subtracting a live position from it gives a negative
// number that a clamp turns into zero — so this drew "-0:00" where the browser
// draws "0:00". A minus sign in front of nothing reads as a broken clock, and it
// is on screen for the whole of every live stream.
//
// The total is the third answer and there was no way to reach it: the web's
// remaining-time element is a BUTTON, and clicking it switches between what is
// left and how long the thing is. Somebody deciding whether to start another
// episode wants the first; somebody who has just opened a film wants the second.
public fun remainingReadout(
    timeSeconds: Double,
    durationSeconds: Double,
    showRemaining: Boolean,
): String {
    // No sign, because there is no quantity to be negative. This is the live
    // stream case and the before-metadata case, and they read the same way.
    if (durationSeconds <= 0.0) return formatSeconds(0.0)

    if (!showRemaining) return formatSeconds(durationSeconds)

    // Clamped, because an engine reporting a position past a duration it has not
    // refreshed yet is ordinary at the end of an item.
    return REMAINING_SIGN + formatSeconds((durationSeconds - timeSeconds).coerceAtLeast(0.0))
}

private const val REMAINING_SIGN = "-"
