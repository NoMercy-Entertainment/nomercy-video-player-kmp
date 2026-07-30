// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals

// The right-hand clock's three answers.
//
// The unknown-duration one is the case that shows. Every live stream reports a
// duration of nought, and subtracting a live position from it gives a negative
// number a clamp turns into zero — so this drew "-0:00" for the whole of every
// live stream where the browser draws "0:00". A minus sign in front of nothing
// reads as a broken clock.
class RemainingReadoutTest {

    @Test
    fun whatIsLeftIsSignedAndCountsDown() {
        assertEquals(
            "-45:00",
            remainingReadout(timeSeconds = 900.0, durationSeconds = 3600.0, showRemaining = true),
        )
    }

    @Test
    fun theOtherReadingIsHowLongTheItemIsWithNoSign() {
        // The web's third answer, and there was no way to reach it: the
        // remaining-time element is a button.
        assertEquals(
            "1:00:00",
            remainingReadout(timeSeconds = 900.0, durationSeconds = 3600.0, showRemaining = false),
        )
    }

    @Test
    fun aLiveStreamReadsZeroWithNoMinusSign() {
        assertEquals(
            "0:00",
            remainingReadout(timeSeconds = 900.0, durationSeconds = 0.0, showRemaining = true),
        )
    }

    @Test
    fun theUnknownDurationCaseIgnoresTheReadingThatWasChosen() {
        // `if (dur <= 0) return formatSeconds(0)` comes FIRST on the web. Asked
        // for the total of an unknown length, the answer is still nought.
        assertEquals(
            "0:00",
            remainingReadout(timeSeconds = 900.0, durationSeconds = 0.0, showRemaining = false),
        )
    }

    @Test
    fun aPositionPastTheEndDoesNotCountBelowZero() {
        // An engine reporting a position past a duration it has not refreshed is
        // ordinary at the end of an item.
        assertEquals(
            "-0:00",
            remainingReadout(timeSeconds = 3700.0, durationSeconds = 3600.0, showRemaining = true),
        )
    }
}
