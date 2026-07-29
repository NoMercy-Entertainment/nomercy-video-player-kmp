// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals

// The two numbers the watchdog runs on, against the app's own.
//
// They are the difference between a loop that recovers focus and one that either
// gives up too early on a slow box or burns a frame budget forever, and neither
// failure is visible in a composable test on a fast machine.
class TvFocusWatchdogTest {

    // Thirty frames is over 400ms at 60fps, which is the room his comment says a
    // slow ARM box needs to place a node. A burst shorter than that gives up
    // while the node it is asking for is still being laid out.
    @Test
    fun aBurstLastsLongEnoughForASlowBox() {
        assertEquals(30, MAX_FRAMES)

        val burstMs: Int = MAX_FRAMES * 1_000 / 60
        assertEquals(true, burstMs > 400, "a burst is only ${burstMs}ms")
    }

    // And the pause after one that never landed. Without it a subtree that is
    // genuinely unfocusable turns the watchdog into a per-frame hot loop.
    @Test
    fun andBacksOffAfterOneThatFailed() {
        assertEquals(500L, BACKOFF_MS)
    }
}
