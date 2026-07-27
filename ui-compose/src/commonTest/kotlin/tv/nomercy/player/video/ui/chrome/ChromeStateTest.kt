// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.ports.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The read model every widget binds to.
//
// Small on purpose, and the derived parts are here rather than at each widget:
// the bar and the scrubber both draw progress, and computing it twice is two
// chances to divide by a zero duration.
class ChromeStateTest {

    @Test
    fun progressIsTheFractionABarDraws() {
        val state = ChromeState(timeSeconds = 30.0, durationSeconds = 120.0)

        assertEquals(0.25f, state.progress)
    }

    @Test
    fun aStreamWithNoKnownLengthReportsNothingRatherThanDividingByZero() {
        // Every live stream. A bar filled from an unknown duration shows
        // whatever the arithmetic produced, which is usually its whole width.
        val state = ChromeState(timeSeconds = 30.0, durationSeconds = 0.0)

        assertEquals(0f, state.progress)
    }

    @Test
    fun aPositionPastTheEndDoesNotOverflow() {
        // Ordinary at the end of a file: the engine reports a position past a
        // duration it has not refreshed yet.
        val state = ChromeState(timeSeconds = 130.0, durationSeconds = 120.0)

        assertEquals(1f, state.progress)
    }

    @Test
    fun theQueueEdgesAreAnsweredHereSoEveryButtonAgrees() {
        // A next button that is enabled on the last item is a button that does
        // nothing, and three widgets computing it is three chances to be off by
        // one in different directions.
        val middle = ChromeState(queueSize = 3, queueIndex = 1)

        assertTrue(middle.hasNext)
        assertTrue(middle.hasPrevious)
    }

    @Test
    fun theLastItemHasNoNext() {
        val last = ChromeState(queueSize = 3, queueIndex = 2)

        assertFalse(last.hasNext)
        assertTrue(last.hasPrevious)
    }

    @Test
    fun theFirstItemHasNoPrevious() {
        val first = ChromeState(queueSize = 3, queueIndex = 0)

        assertTrue(first.hasNext)
        assertFalse(first.hasPrevious)
    }

    @Test
    fun anEmptyQueueOffersNeither() {
        val empty = ChromeState()

        assertFalse(empty.hasNext)
        assertFalse(empty.hasPrevious)
    }

    @Test
    fun captionsOffIsAChoiceRatherThanAnAbsence() {
        // Null is the row a menu offers, not a track that failed to load, which
        // is why it is nullable rather than an empty label.
        val state = ChromeState(
            subtitleTracks = listOf(SubtitleTrack(id = "en", language = "en", label = "English")),
            activeSubtitle = null,
        )

        assertNull(state.activeSubtitle)
        assertEquals(1, state.subtitleTracks.size)
    }

    @Test
    fun nothingIsShowingBeforeAnythingHasLoaded() {
        // The default is what a chrome mounts with, and it has to be drawable:
        // a player built and not yet given a film still renders.
        val state = ChromeState()

        assertFalse(state.playing)
        assertEquals(0f, state.progress)
        assertNull(state.item)
        assertNull(state.message)
    }
}
