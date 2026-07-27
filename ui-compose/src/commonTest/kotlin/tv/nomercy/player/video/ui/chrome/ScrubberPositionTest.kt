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

// Where along the film a drag position falls.
//
// The arithmetic rather than the gesture, because this is the part that produces
// a seek to the wrong place and it is the same on every platform.
class ScrubberPositionTest {

    @Test
    fun theMiddleOfTheStripIsTheMiddleOfTheFilm() {
        assertEquals(60.0, secondsAt(x = 50f, width = 100f, durationSeconds = 120.0))
    }

    @Test
    fun theLeftEdgeIsTheStart() {
        assertEquals(0.0, secondsAt(x = 0f, width = 100f, durationSeconds = 120.0))
    }

    @Test
    fun aDragThatLeavesTheStripDoesNotSeekPastTheEnd() {
        // A drag reports positions outside the element it started in, and an
        // unclamped answer is a seek past the end of the file.
        assertEquals(120.0, secondsAt(x = 400f, width = 100f, durationSeconds = 120.0))
    }

    @Test
    fun norBeforeTheStart() {
        assertEquals(0.0, secondsAt(x = -80f, width = 100f, durationSeconds = 120.0))
    }

    @Test
    fun aStreamWithNoKnownLengthHasNowhereToDragTo() {
        // Every live stream. Without this the fraction multiplies by zero at
        // best and divides by it at worst.
        assertEquals(0.0, secondsAt(x = 50f, width = 100f, durationSeconds = 0.0))
    }

    @Test
    fun aStripWithNoWidthYetIsNotDividedBy() {
        // The first frame after mount, before layout has measured anything.
        assertEquals(0.0, secondsAt(x = 50f, width = 0f, durationSeconds = 120.0))
    }
}
