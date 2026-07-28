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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedbackOverlayTest {

    private val strings = FeedbackStrings(
        loading = "Loading",
        buffering = "Buffering",
        error = "Something went wrong",
    )

    private fun next(current: FeedbackState, event: FeedbackEvent) =
        nextFeedbackState(current, event, strings)

    private fun after(vararg events: FeedbackEvent): FeedbackState =
        events.fold(FeedbackState()) { state, event -> next(state, event) }

    @Test
    fun mountingSaysLoadingAndSpins() {
        val state = after(FeedbackEvent.Mounted)

        assertEquals("Loading", state.text)
        assertTrue(state.buffering)
        assertTrue(state.visible)
    }

    @Test
    fun waitingAndStallingBothSayBuffering() {
        assertEquals("Buffering", after(FeedbackEvent.Waiting).text)
        assertEquals("Buffering", after(FeedbackEvent.Stalled).text)
    }

    @Test
    fun playbackResumingClearsThePlayersOwnMessage() {
        val state = after(FeedbackEvent.Waiting, FeedbackEvent.Playing)

        assertFalse(state.visible)
        assertFalse(state.buffering)
    }

    @Test
    fun aTimeUpdateClearsItToo() {
        assertFalse(after(FeedbackEvent.Waiting, FeedbackEvent.Progressed).visible)
    }

    // The rule that gets lost. Without it a caller's message is wiped by the
    // next time update, which lands within a second of it being raised.
    @Test
    fun playbackResumingDoesNotClearSomebodyElsesMessage() {
        val state = after(
            FeedbackEvent.Display("Skipping intro"),
            FeedbackEvent.Playing,
            FeedbackEvent.Progressed,
        )

        assertEquals("Skipping intro", state.text)
        assertTrue(state.visible)
    }

    // ...but the spinner still stops, or a message sits over moving video with
    // a spinner turning behind it.
    @Test
    fun theSpinnerStopsEvenWhenTheMessageStays() {
        val state = after(
            FeedbackEvent.Waiting,
            FeedbackEvent.Display("Skipping intro"),
            FeedbackEvent.Playing,
        )

        assertFalse(state.buffering)
        assertTrue(state.visible)
    }

    // An error leaves text up with no spinner. A spinner under an error message
    // reads as "still trying", which it is not.
    @Test
    fun anErrorStopsTheSpinnerAndKeepsTheText() {
        val state = after(FeedbackEvent.Waiting, FeedbackEvent.Failed)

        assertEquals("Something went wrong", state.text)
        assertFalse(state.buffering)
    }

    // The error is the player's own message, so recovering clears it.
    @Test
    fun recoveringFromAnErrorClearsIt() {
        assertFalse(after(FeedbackEvent.Failed, FeedbackEvent.Playing).visible)
    }

    @Test
    fun aNewItemGoesBackToLoading() {
        val state = after(FeedbackEvent.Playing, FeedbackEvent.ItemChanged)

        assertEquals("Loading", state.text)
        assertTrue(state.buffering)
    }

    @Test
    fun aTimedMessageCarriesItsDuration() {
        assertEquals(2_000L, after(FeedbackEvent.Display("Hi", 2_000)).hideAfterMs)
    }

    // `ms > 0` in the web. A zero duration is "no timer", not "hide instantly",
    // and treating it as the latter makes the message flash and vanish.
    @Test
    fun aZeroDurationMeansNoTimerRatherThanHideNow() {
        val state = after(FeedbackEvent.Display("Hi", 0))

        assertTrue(state.visible)
        assertNull(state.hideAfterMs)
    }

    @Test
    fun removeClearsTheTextButLeavesTheSpinnerAlone() {
        val state = after(
            FeedbackEvent.Waiting,
            FeedbackEvent.Display("Hi"),
            FeedbackEvent.Remove,
        )

        assertFalse(state.visible)
        assertTrue(state.buffering)
    }
}
