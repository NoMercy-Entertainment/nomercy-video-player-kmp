// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

// A position on a progress bar, as a person reads it.
//
// Hours only when there are any. A twenty minute episode showing 0:20:00 makes
// somebody count the fields to work out which is which, and every player they
// have ever used writes it the short way.
public fun formatTime(seconds: Double): String {
    // Negative arrives from a remaining-time subtraction that crossed zero at
    // the end of a file, and a bar reading "-0:-1" looks broken in a way that
    // gets reported as a playback bug.
    val whole: Int = seconds.toInt().coerceAtLeast(0)
    val hours: Int = whole / SECONDS_PER_HOUR
    val minutes: Int = (whole % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val remainder: Int = whole % SECONDS_PER_MINUTE

    return if (hours > 0) {
        "$hours:${twoDigits(minutes)}:${twoDigits(remainder)}"
    } else {
        "$minutes:${twoDigits(remainder)}"
    }
}

// Minutes and seconds always carry two digits once there is a field to their
// left, or 1:5 reads as one minute five rather than one minute and five seconds.
private fun twoDigits(value: Int): String = if (value < TEN) "0$value" else "$value"

private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60
private const val TEN = 10
