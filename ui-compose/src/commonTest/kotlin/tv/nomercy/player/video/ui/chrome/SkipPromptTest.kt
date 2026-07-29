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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The skip rules, against SkipUtils in his ChapterAutoSkipPlugin.
//
// Silent in both directions if it drifts: a prompt that never appears reads as a
// source with no chapters, and one that appears on the wrong chapter offers to
// skip the film. Neither shows up in a screenshot.
class SkipPromptTest {

    @Test
    fun theAnimeShorthandIsRecognisedAtBothEnds() {
        assertEquals(SkipKind.Intro, SkipPrompt.typeOf("OP"))
        assertEquals(SkipKind.Intro, SkipPrompt.typeOf("NCOP"))
        assertEquals(SkipKind.Outro, SkipPrompt.typeOf("ED"))
        assertEquals(SkipKind.Outro, SkipPrompt.typeOf("NCED"))
    }

    @Test
    fun theSpeltOutTitlesAreTooAndCaseDoesNotMatter() {
        assertEquals(SkipKind.Intro, SkipPrompt.typeOf("opening credits"))
        assertEquals(SkipKind.Outro, SkipPrompt.typeOf("END CREDITS"))
        assertEquals(SkipKind.Outro, SkipPrompt.typeOf("Next Episode Preview"))
    }

    // "^Opening" without the anchor at the end, so a chapter called "Opening
    // Sequence" still counts. That prefix arm is easy to drop when transcribing
    // a list of otherwise fully-anchored patterns.
    @Test
    fun anOpeningPrefixStillCountsAsAnOpening() {
        assertEquals(SkipKind.Intro, SkipPrompt.typeOf("Opening Sequence"))
    }

    @Test
    fun anOrdinaryChapterIsNeither() {
        assertNull(SkipPrompt.typeOf("Act One"))
        assertNull(SkipPrompt.typeOf("The Reveal"))
        // Contains "credits" but does not start with it, and the patterns are
        // anchored — a chapter about the credits is not the credits.
        assertNull(SkipPrompt.typeOf("Before the Credits"))
    }

    // The first guard: on the FIRST item, an opening in the first half is not
    // offered. Skipping it there is skipping the start of the only thing playing.
    @Test
    fun anOpeningIsNotOfferedEarlyInTheFirstItem() {
        assertFalse(SkipPrompt.shouldOffer("OP", SkipPosition(1200.0, 60.0, index = 0, playlistSize = 4)))
    }

    @Test
    fun butItIsOfferedEarlyInAnyOtherItem() {
        assertTrue(SkipPrompt.shouldOffer("OP", SkipPosition(1200.0, 60.0, index = 1, playlistSize = 4)))
    }

    // The second guard: on the LAST item, an ending in the second half is not
    // offered, because there is nothing after it to skip to.
    @Test
    fun anEndingIsNotOfferedLateInTheLastItem() {
        assertFalse(SkipPrompt.shouldOffer("ED", SkipPosition(1200.0, 1100.0, index = 3, playlistSize = 4)))
    }

    @Test
    fun butItIsOfferedLateInAnyOtherItem() {
        assertTrue(SkipPrompt.shouldOffer("ED", SkipPosition(1200.0, 1100.0, index = 2, playlistSize = 4)))
    }

    // A single-item playlist is both the first and the last, so both guards
    // apply and neither end is skippable. That is the edge his comment names.
    @Test
    fun aSingleItemOffersNeitherEnd() {
        assertFalse(SkipPrompt.shouldOffer("OP", SkipPosition(1200.0, 60.0, index = 0, playlistSize = 1)))
        assertFalse(SkipPrompt.shouldOffer("ED", SkipPosition(1200.0, 1100.0, index = 0, playlistSize = 1)))
    }

    @Test
    fun anUnskippableTitleIsNeverOfferedWhereverItSits() {
        assertFalse(SkipPrompt.shouldOffer("Act One", SkipPosition(1200.0, 600.0, index = 1, playlistSize = 4)))
    }

    // TvChapter carries a start and no end, so a chapter runs until the next
    // begins and the last runs to the end of the film. Deriving that wrongly
    // would put the prompt on the chapter next door.
    @Test
    fun theChapterAtAPositionRunsUntilTheNextOne() {
        val chapters = listOf(
            TvChapter(startSeconds = 0.0, title = "OP"),
            TvChapter(startSeconds = 90.0, title = "Act One"),
            TvChapter(startSeconds = 600.0, title = "ED"),
        )

        assertEquals("OP", SkipPrompt.chapterAt(chapters, 10.0)?.title)
        assertEquals("Act One", SkipPrompt.chapterAt(chapters, 300.0)?.title)
        assertEquals("ED", SkipPrompt.chapterAt(chapters, 9_999.0)?.title)
    }

    @Test
    fun theTimingsAreHisTimings() {
        assertEquals(10_000L, SkipPrompt.VISIBLE_MS)
        assertEquals(0.5, SkipPrompt.SAME_CHAPTER_SECONDS)
    }
}
