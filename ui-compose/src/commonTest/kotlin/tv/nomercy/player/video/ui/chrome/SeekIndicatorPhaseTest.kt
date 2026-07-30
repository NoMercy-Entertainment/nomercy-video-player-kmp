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

// The disc's life, which is three states and not two.
//
// The web arms two timers per tap: the run collapses at a second, and the visible
// class comes off 200ms after that. So the figure holds still for a fifth of a
// second and only then fades. Drawn as on-or-off at the collapse — which is what
// this did — the disc vanishes mid-gesture and reads as a glitch.
class SeekIndicatorPhaseTest {

    @Test
    fun nothingIsDrawnBeforeAnybodyHasTapped() {
        assertEquals(SeekIndicatorPhase.Gone, seekIndicatorPhase(SeekRun(), nowMs = 0, options = OPTIONS))
    }

    @Test
    fun theDiscIsDrawnWhileTheRunIsLive() {
        assertEquals(SeekIndicatorPhase.Shown, phaseAt(500))
    }

    @Test
    fun itHoldsStillForTheHoldAfterTheRunEnds() {
        // Past the collapse at a second, inside the 200ms hold. This is the
        // stretch the whole three-state model exists for.
        assertEquals(SeekIndicatorPhase.Shown, phaseAt(1_100))
    }

    @Test
    fun itIsToldToHideWhenTheHoldRunsOut() {
        assertEquals(SeekIndicatorPhase.Fading, phaseAt(1_200))
    }

    @Test
    fun itStaysComposedForTheWholeFade() {
        // A composable removed the instant it is told to hide never finishes its
        // 120ms transition, so it pops out of existence instead of fading.
        assertEquals(SeekIndicatorPhase.Fading, phaseAt(1_319))
    }

    @Test
    fun itIsGoneOnceTheFadeIsDone() {
        assertEquals(SeekIndicatorPhase.Gone, phaseAt(1_320))
    }

    private fun phaseAt(nowMs: Long): SeekIndicatorPhase =
        seekIndicatorPhase(
            SeekRun(side = SeekSide.Forward, seconds = 30f, lastTapMs = 0),
            nowMs = nowMs,
            options = OPTIONS,
        )

    private companion object {
        // The defaults, which are the web's own two timers.
        val OPTIONS = TouchZonesOptions()
    }
}
