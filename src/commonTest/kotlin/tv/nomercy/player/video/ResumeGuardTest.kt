// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlin.test.Test
import kotlin.test.assertEquals

// Ported case-for-case from the deprecated app's `ResumeGuardTest` (R6 —
// "has its own test today, so the behaviour is known and portable"). Same
// inputs, same expected outputs; only the harness changed (JUnit -> kotlin.test).
class ResumeGuardTest {

    private val duration = 3600L // 1 hour in seconds

    // ── Should RESUME (return savedSeconds * 1000) ─────────────────────────

    @Test
    fun fiftyPercentProgressResumesNormally() {
        val saved = (duration * 0.50).toLong() // 1800s
        assertEquals(saved * 1000L, ResumeGuard.startPositionMs(saved, duration))
    }

    @Test
    fun ninetyPercentProgressResumesNormally() {
        // 90% is 3240s of 3600s — still 360s from end, under both thresholds
        val saved = (duration * 0.90).toLong() // 3240s
        assertEquals(saved * 1000L, ResumeGuard.startPositionMs(saved, duration))
    }

    @Test
    fun exactlyNinetyFourPercentResumesNormally() {
        val saved = (duration * 0.94).toLong() // 3384s
        assertEquals(saved * 1000L, ResumeGuard.startPositionMs(saved, duration))
    }

    @Test
    fun trailingGuardFiresIndependentlyOfPercentGuardForShortDurationContent() {
        // 100s clip: 95% threshold = 95s; trailing window starts at 70s (100-30).
        // At 72s: 72% (below percent threshold) but within trailing 30s -> guard fires.
        val shortDuration = 100L
        val saved = 72L
        assertEquals(0L, ResumeGuard.startPositionMs(saved, shortDuration))
    }

    @Test
    fun outsideTrailingWindowAndUnderPercentThresholdResumesNormally() {
        val shortDuration = 100L
        val saved = 60L // 60%, 40s from end
        assertEquals(saved * 1000L, ResumeGuard.startPositionMs(saved, shortDuration))
    }

    // ── Should RESTART (return 0) ───────────────────────────────────────────

    @Test
    fun ninetyFivePercentProgressTriggersGuard() {
        val saved = (duration * 0.95).toLong() // 3420s
        assertEquals(0L, ResumeGuard.startPositionMs(saved, duration))
    }

    @Test
    fun ninetyNinePercentProgressTriggersGuard() {
        val saved = (duration * 0.99).toLong() // 3564s
        assertEquals(0L, ResumeGuard.startPositionMs(saved, duration))
    }

    @Test
    fun oneHundredPercentProgressTriggersGuard() {
        assertEquals(0L, ResumeGuard.startPositionMs(duration, duration))
    }

    @Test
    fun savedEqualsDurationMinusThirtySecondsTriggersTrailingGuard() {
        val saved = duration - 30L // exactly 30s from end
        assertEquals(0L, ResumeGuard.startPositionMs(saved, duration))
    }

    @Test
    fun savedEqualsDurationMinusTwentyNineSecondsTriggersTrailingGuard() {
        val saved = duration - 29L // 29s from end
        assertEquals(0L, ResumeGuard.startPositionMs(saved, duration))
    }

    @Test
    fun savedEqualsDurationMinusOneSecondTriggersGuard() {
        val saved = duration - 1L
        assertEquals(0L, ResumeGuard.startPositionMs(saved, duration))
    }

    // ── Edge: short content (e.g. trailer ~90s) ─────────────────────────────

    @Test
    fun shortContentFiftyPercentOfNinetySecondsResumesNormally() {
        val shortDuration = 90L
        val saved = 45L // exactly 50%
        assertEquals(saved * 1000L, ResumeGuard.startPositionMs(saved, shortDuration))
    }

    @Test
    fun shortContentLastThirtySecondsOfNinetySecondsTriggersTrailingGuard() {
        val shortDuration = 90L
        val saved = 65L // 25s from end
        assertEquals(0L, ResumeGuard.startPositionMs(saved, shortDuration))
    }

    @Test
    fun shortContentNinetySixPercentOfNinetySecondsTriggersPercentGuard() {
        val shortDuration = 90L
        val saved = 87L // ~96.7%
        assertEquals(0L, ResumeGuard.startPositionMs(saved, shortDuration))
    }

    // ── Threshold constants sanity ───────────────────────────────────────────

    @Test
    fun trailingThresholdConstantIsThirtySeconds() {
        assertEquals(30L, ResumeGuard.TRAILING_SECONDS)
    }

    @Test
    fun percentThresholdConstantIsNinetyFivePercent() {
        assertEquals(95f, ResumeGuard.PERCENT_THRESHOLD)
    }
}
