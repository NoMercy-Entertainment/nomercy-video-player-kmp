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
import kotlin.test.assertNull

// Both rules the first native version got wrong.
//
// Neither shows up in a screenshot and both show up under a finger: a grace
// window three times too wide swallows a deliberate second press, and a forward
// button that seeks to the end turns a dead control into one that ends the
// episode.
class ChapterNavigationTest {

    private val starts = listOf(0.0, 90.0, 600.0, 1300.0)

    // Back restarts the chapter you are in, the way a CD player does. This
    // reads as surprising until you press it: twenty seconds into a chapter,
    // back means "start this again".
    @Test
    fun backRestartsTheCurrentChapter() {
        assertEquals(600.0, previousChapterStart(starts, timeSeconds = 620.0))
    }

    // And pressing it a second time, now sitting on the boundary, moves back
    // one. That is what the grace window is for.
    @Test
    fun pressingBackTwiceReachesThePreviousChapter() {
        val once: Double = previousChapterStart(starts, timeSeconds = 620.0)
        val twice: Double = previousChapterStart(starts, timeSeconds = once)

        assertEquals(600.0, once)
        assertEquals(90.0, twice)
    }

    // One second, not three. Within it, back moves to the previous chapter;
    // past it, back restarts the current one. A three-second window puts the
    // second press in the same place as the first.
    @Test
    fun theGraceWindowIsOneSecond() {
        // 0.5s in: still within the window, so back moves off this chapter.
        assertEquals(0.0, previousChapterStart(starts, timeSeconds = 90.5))
        // 1.5s in: past it, so back restarts this chapter.
        assertEquals(90.0, previousChapterStart(starts, timeSeconds = 91.5))
    }

    @Test
    fun backFromTheFirstChapterGoesToZero() {
        assertEquals(0.0, previousChapterStart(starts, timeSeconds = 30.0))
    }

    // Both rules the first native version got wrong were mine, and this suite
    // is what corrected them: the grace was three seconds and forward seeked to
    // the end. The second of those was caught by reading; the first was caught
    // by a test failing on an expectation that was itself wrong, which is the
    // only way that one surfaces short of a finger on a remote.

    @Test
    fun backWithNoChaptersGoesToZero() {
        assertEquals(0.0, previousChapterStart(emptyList(), timeSeconds = 300.0))
    }

    @Test
    fun forwardJumpsToTheNextBoundary() {
        assertEquals(600.0, nextChapterStart(starts, timeSeconds = 120.0))
    }

    // Past the last boundary it does NOTHING. Seeking to the end instead would
    // turn a dead button into one that ends the episode.
    @Test
    fun forwardPastTheLastChapterIsANoOp() {
        assertNull(nextChapterStart(starts, timeSeconds = 1400.0))
    }

    @Test
    fun forwardHasTheSameGrace() {
        // 0.5s before a boundary is inside the window, so it skips to the one
        // after rather than to the boundary a viewer is already arriving at.
        assertEquals(1300.0, nextChapterStart(starts, timeSeconds = 599.5))
    }

    @Test
    fun forwardWithNoChaptersIsANoOp() {
        assertNull(nextChapterStart(emptyList(), timeSeconds = 10.0))
    }

    // Sorted first, so a chapter list that arrived out of order still navigates
    // in time order.
    @Test
    fun anUnorderedListStillNavigatesInTimeOrder() {
        val jumbled = listOf(600.0, 0.0, 1300.0, 90.0)

        assertEquals(600.0, previousChapterStart(jumbled, timeSeconds = 620.0))
        assertEquals(600.0, nextChapterStart(jumbled, timeSeconds = 120.0))
    }
}
