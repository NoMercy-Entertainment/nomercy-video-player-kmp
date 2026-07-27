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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// What the seek indicator reads during a run of double-taps.
//
// Somebody skipping an opening taps four times in a second. Four separate "10
// seconds" flashes tell them nothing about where they have got to; what they
// want to read is forty.
class SeekAccumulatorTest {

    private val window = 1_000L
    private val step = 10f

    @Test
    fun oneTapReadsAsOneStep() {
        val run = SeekRun().plus(SeekSide.Forward, step, nowMs = 0, windowMs = window)

        assertEquals("+10s", run.label)
    }

    @Test
    fun tapsInARunAddUp() {
        var run = SeekRun().plus(SeekSide.Forward, step, nowMs = 0, windowMs = window)
        run = run.plus(SeekSide.Forward, step, nowMs = 200, windowMs = window)
        run = run.plus(SeekSide.Forward, step, nowMs = 400, windowMs = window)

        assertEquals("+30s", run.label)
    }

    @Test
    fun aTapAfterThePauseStartsAgain() {
        // The figure describes one continuous gesture. Carrying it across a
        // pause would show a total from a skip somebody finished a minute ago.
        var run = SeekRun().plus(SeekSide.Forward, step, nowMs = 0, windowMs = window)

        run = run.plus(SeekSide.Forward, step, nowMs = 5_000, windowMs = window)

        assertEquals("+10s", run.label)
    }

    @Test
    fun tappingTheOtherSideStartsAgainRatherThanSubtracting() {
        // Two directions in one figure is a number that goes down while the film
        // goes forward.
        var run = SeekRun().plus(SeekSide.Forward, step, nowMs = 0, windowMs = window)

        run = run.plus(SeekSide.Back, step, nowMs = 100, windowMs = window)

        assertEquals("-10s", run.label)
    }

    @Test
    fun theSignIsTheWholeMessage() {
        // The indicator has no other label. Backwards reading as "10s" is a
        // control that looks like it did the opposite of what it did.
        val back = SeekRun().plus(SeekSide.Back, step, nowMs = 0, windowMs = window)

        assertTrue(back.label.startsWith("-"))
    }

    @Test
    fun itIsOnScreenWhileTheRunIsLive() {
        val run = SeekRun().plus(SeekSide.Forward, step, nowMs = 0, windowMs = window)

        assertTrue(run.isVisible(nowMs = 500, windowMs = window))
    }

    @Test
    fun andGoesAwayWhenTheRunEnds() {
        val run = SeekRun().plus(SeekSide.Forward, step, nowMs = 0, windowMs = window)

        assertFalse(run.isVisible(nowMs = 1_500, windowMs = window))
    }

    @Test
    fun nothingIsShownBeforeAnybodyHasTapped() {
        assertFalse(SeekRun().isVisible(nowMs = 0, windowMs = window))
        assertEquals("", SeekRun().label)
    }
}
