// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

import kotlin.test.Test
import kotlin.test.assertEquals

// The number under a progress bar.
class FormatTimeTest {

    @Test
    fun aShortEpisodeDoesNotCarryAnHoursField() {
        // 0:20:00 makes somebody count the fields to work out which is which,
        // and every player they have used writes it the short way.
        assertEquals("20:00", formatTime(1200.0))
    }

    @Test
    fun aFilmDoes() {
        assertEquals("1:01:05", formatTime(3665.0))
    }

    @Test
    fun secondsKeepTwoDigitsOnceThereIsAFieldToTheirLeft() {
        // 1:5 reads as one minute five, not one minute and five seconds.
        assertEquals("1:05", formatTime(65.0))
    }

    @Test
    fun soDoMinutesOnceThereIsAnHour() {
        assertEquals("2:05:00", formatTime(7500.0))
    }

    @Test
    fun theStartOfAFileIsZero() {
        assertEquals("0:00", formatTime(0.0))
    }

    @Test
    fun aNegativeArrivesFromASubtractionAndIsNotShownAsOne() {
        // Remaining time crosses zero at the end of a file, and a bar reading
        // "-0:-1" gets reported as a playback bug rather than a formatting one.
        assertEquals("0:00", formatTime(-3.0))
    }

    @Test
    fun fractionsOfASecondAreDroppedRatherThanRounded() {
        // A position ticking to 1:00 before the second has elapsed shows a bar
        // ahead of the picture, which is the one place it is visibly wrong.
        assertEquals("0:59", formatTime(59.99))
    }
}
