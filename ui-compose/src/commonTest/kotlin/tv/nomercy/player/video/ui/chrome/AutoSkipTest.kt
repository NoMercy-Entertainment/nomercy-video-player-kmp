// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.video.tv.TvChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The seek his ChapterAutoSkipPlugin performs, and the guard that stops it
// performing it twice.
class AutoSkipTest {

    private val chapters = listOf(
        TvChapter(0.0, "Opening Credits"),
        TvChapter(90.0, "Episode"),
        TvChapter(1_400.0, "Ending"),
        TvChapter(1_500.0, "Preview"),
    )

    // Item two of three, so neither playlist guard applies and what is left is
    // the auto-skip's own decision. The first item's opening is watched, which
    // theFirstItemsOpeningIsLeftAlone pins separately.
    private fun at(seconds: Double, index: Int = 1, size: Int = 3) = SkipPosition(
        durationSeconds = 1_600.0,
        currentSeconds = seconds,
        index = index,
        playlistSize = size,
    )

    @Test
    fun itSeeksToTheEndOfAnOpening() {
        assertEquals(90.0, AutoSkipTracker().targetFor(chapters, at(30.0)))
    }

    @Test
    fun andToTheEndOfAnEnding() {
        assertEquals(1_500.0, AutoSkipTracker().targetFor(chapters, at(1_450.0)))
    }

    @Test
    fun itStaysPutInsideTheEpisodeItself() {
        assertNull(AutoSkipTracker().targetFor(chapters, at(600.0)))
    }

    // The one that matters after the first skip: a viewer who scrubs back into
    // the opening deliberately is not carried out of it again.
    @Test
    fun aChapterIsCarriedPastOnlyOnce() {
        val tracker = AutoSkipTracker()

        assertEquals(90.0, tracker.targetFor(chapters, at(30.0)))
        assertNull(tracker.targetFor(chapters, at(30.0)))
        assertNull(tracker.targetFor(chapters, at(45.0)))
    }

    @Test
    fun andIsArmedAgainOnTheNextItem() {
        val tracker = AutoSkipTracker()

        tracker.targetFor(chapters, at(30.0))
        tracker.forget()

        assertEquals(90.0, tracker.targetFor(chapters, at(30.0)))
    }

    // The two playlist guards are SkipPrompt's, and this proves the tracker
    // actually asks: an opening at the very start of the FIRST item is watched,
    // not skipped, because that is the recap somebody just chose to play.
    @Test
    fun theFirstItemsOpeningIsLeftAlone() {
        val early = listOf(TvChapter(0.0, "Opening"), TvChapter(60.0, "Episode"))

        assertNull(
            AutoSkipTracker().targetFor(
                early,
                SkipPosition(durationSeconds = 1_600.0, currentSeconds = 5.0, index = 0, playlistSize = 3),
            ),
        )
    }

    // Nothing after the ending means nowhere to seek. Staying put is right, and
    // so is staying armed: the item can gain a chapter after it.
    @Test
    fun anEndingWithNothingAfterItStaysArmed() {
        val trailing = listOf(TvChapter(0.0, "Episode"), TvChapter(1_400.0, "Ending"))
        val tracker = AutoSkipTracker()
        val position = SkipPosition(
            durationSeconds = 1_600.0,
            currentSeconds = 1_450.0,
            index = 1,
            playlistSize = 3,
        )

        assertNull(tracker.targetFor(trailing, position))
        assertEquals(1_500.0, tracker.targetFor(trailing + TvChapter(1_500.0, "Preview"), position))
    }
}
