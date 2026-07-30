// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.chapters

import tv.nomercy.player.core.media.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals

// The line the info panel reads out.
//
// The direction of the walk is the whole test. Every chapter before the playhead
// has started, so a search from the front names chapter one for the whole film —
// and that is not a crash, it is an info panel that is confidently wrong.
class ChapterLabelTest {

    @Test
    fun theChapterNamedIsTheLastOneThatHasStarted() {
        assertEquals(
            "Chapter 3: The Crossing",
            resolveChapterLabel(FILM, currentTime = 700.0, chapterWord = "Chapter"),
        )
    }

    @Test
    fun theFirstChapterIsNumberOneAndNotZero() {
        assertEquals(
            "Chapter 1: Titles",
            resolveChapterLabel(FILM, currentTime = 10.0, chapterWord = "Chapter"),
        )
    }

    @Test
    fun theLastChapterHoldsUntilTheEnd() {
        assertEquals(
            "Chapter 4: Credits",
            resolveChapterLabel(FILM, currentTime = 5000.0, chapterWord = "Chapter"),
        )
    }

    @Test
    fun anUnnamedChapterIsAnnouncedByItsNumberAlone() {
        // A scan that produced breaks and no titles. The web drops the colon
        // rather than printing one with nothing after it.
        val unnamed: List<Chapter> = listOf(Chapter(startTime = 0.0, title = ""))

        assertEquals("Chapter 1", resolveChapterLabel(unnamed, 5.0, chapterWord = "Chapter"))
    }

    @Test
    fun aFilmWithoutChaptersSaysNothing() {
        assertEquals("", resolveChapterLabel(emptyList(), 700.0, chapterWord = "Chapter"))
    }

    @Test
    fun aPositionBeforeTheFirstChapterSaysNothing() {
        // A list whose first chapter starts partway in and has no filler yet.
        // Naming chapter one there tells the viewer they are somewhere they
        // are not.
        val late: List<Chapter> = listOf(Chapter(startTime = 60.0, title = "Act One"))

        assertEquals("", resolveChapterLabel(late, 10.0, chapterWord = "Chapter"))
    }

    @Test
    fun theWordIsWhateverTheHostPassesIn() {
        // Hoofdstuk, Kapitel, Chapitre. Hardcoding the English would be a
        // television speaking English in seventy-eight locales.
        assertEquals(
            "Hoofdstuk 3: The Crossing",
            resolveChapterLabel(FILM, currentTime = 700.0, chapterWord = "Hoofdstuk"),
        )
    }

    private companion object {
        val FILM: List<Chapter> = listOf(
            Chapter(startTime = 0.0, title = "Titles"),
            Chapter(startTime = 300.0, title = "Arrival"),
            Chapter(startTime = 600.0, title = "The Crossing"),
            Chapter(startTime = 4200.0, title = "Credits"),
        )
    }
}
