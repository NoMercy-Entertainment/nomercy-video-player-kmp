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
import tv.nomercy.player.core.ports.bufferedFrontier
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

// What the buffered bar is told, once the engine has holes in it.
//
// The walk has its own test in core; this one grades the WIRING, which is the half
// that was actually missing. The bar has to read the frontier off the PLAYER — the
// per-frame snapshot carries a `buffered` field too, and reading that one draws a
// promise over a hole. A test on the walk alone stays green with the call site
// pointed at either source.
class ChromeBufferedRangesTest {

    @Test
    fun theBarIsFilledToTheRangeHoldingThePlayheadNotToTheLastOne() {
        val player = NMVideoPlayer(SplitBufferBackend())

        val state: ChromeState = chromeStateOf(
            player,
            PlayerState(time = 5.0, duration = 3600.0, buffered = 3600.0),
            item = null,
        )

        // 90 of 3600, against a snapshot that says 3600. The snapshot field is the
        // decoy: reading `buffered` off the per-frame state instead of off the
        // player gives a full bar, and the hour it promises sits on the far side of
        // a hole the viewer would drag straight into.
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

// An engine that kept what it had across a seek backwards, which is AVFoundation's
// shape: it reports where the data is and lets the frontier follow from that.
//
// The two used to disagree here on purpose — `buffered` named the end of the film
// while the ranges said the middle hour was dropped — because a backend answered
// the frontier independently of its own ranges and could contradict them. It
// cannot now: MediaBackend derives the frontier from the ranges for every engine
// that reports them, so a fake that contradicted itself would be modelling an
// engine the contract no longer allows.
private class SplitBufferBackend : RecordingVideoBackend() {
    override fun bufferedRanges(): List<TimeRange> = listOf(
        TimeRange(0.0, 90.0),
        TimeRange(3500.0, 3600.0),
    )

    // Restating the interface default, because the base fake overrides `buffered`
    // with a fixed number to stand in for a frontier-only engine and this one is
    // not that.
    override fun buffered(): Double = bufferedFrontier(bufferedRanges(), currentTime())
}
