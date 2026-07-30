// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.player.PlayerState
import tv.nomercy.player.core.ports.TimeRange
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

// What the buffered bar is told, once the engine has holes in it.
//
// The function has its own test; this one grades the WIRING, which is the half
// that was actually missing. The projection read the engine's single `buffered`
// number straight through, so an engine that could report ranges had them
// ignored and the bar drew a promise over a hole. A test on the function alone
// stays green with the call site reverted.
class ChromeBufferedRangesTest {

    @Test
    fun theBarIsFilledToTheRangeHoldingThePlayheadNotToTheLastOne() {
        val player = NMVideoPlayer(SplitBufferBackend())

        val state: ChromeState = chromeStateOf(
            player,
            PlayerState(time = 5.0, duration = 3600.0, buffered = 3600.0),
            item = null,
        )

        // 90 of 3600. Reading the engine's own `buffered` gives a full bar, which
        // is the defect: the hour of buffer it names sits on the far side of a
        // hole the viewer would drag straight into.
        assertEquals(90.0f / 3600.0f, state.bufferedFraction)
    }

    @Test
    fun anEngineReportingOneFrontierIsUnchanged() {
        // Media3 and libVLC. TimeController states their single number as one
        // range from the start, so the bar has to keep reading exactly that — a
        // walk that mishandled the synthesised shape would empty every buffered
        // bar on Android and desktop at once.
        val player = NMVideoPlayer(RecordingVideoBackend())

        val state: ChromeState = chromeStateOf(
            player,
            PlayerState(time = 30.0, duration = 240.0, buffered = 120.0),
            item = null,
        )

        assertEquals(0.5f, state.bufferedFraction)
    }
}

// An engine that kept what it had across a seek backwards.
//
// The ranges and the frontier disagree on purpose, which is the real shape:
// `buffered` says data reaches the end of the film and the ranges say the middle
// hour of it was dropped.
private class SplitBufferBackend : RecordingVideoBackend() {
    override fun bufferedRanges(): List<TimeRange> = listOf(
        TimeRange(0.0, 90.0),
        TimeRange(3500.0, 3600.0),
    )
}
