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
import kotlin.test.assertTrue

// Three of the four rules only appear on content most people never test with:
// untitled episodes, specials, and items with no season at all.
class TitleBarTextTest {

    private fun text(item: TvChromeItem?) = titleBarText(
        item = item,
        seasonLabel = { "S$it" },
        episodeLabel = { "E$it" },
        extrasLabel = { "Extras" },
    )

    @Test
    fun aFilmPutsItsOwnTitleOnTop() {
        val result = text(TvChromeItem(title = "Sintel", playlistType = "movie"))

        assertEquals("Sintel", result.primary)
        assertEquals("", result.secondary)
        assertFalse(result.showsSecondary)
    }

    @Test
    fun aSeriesPutsTheShowOnTopAndTheEpisodeUnderneath() {
        val result = text(
            TvChromeItem(title = "The Pilot", show = "Doctor Who", season = 10, episode = 1),
        )

        assertEquals("Doctor Who", result.primary)
        assertEquals("S10E1 • The Pilot", result.secondary)
        assertTrue(result.showsSecondary)
    }

    // Servers write the show name as the title for untitled episodes. Without
    // the check the player prints it twice, one line under the other.
    @Test
    fun anEpisodeTitledAfterItsShowIsNotRepeated() {
        val result = text(
            TvChromeItem(title = "Doctor Who", show = "Doctor Who", season = 10, episode = 1),
        )

        assertEquals("Doctor Who", result.primary)
        assertEquals("S10E1", result.secondary)
    }

    // Season 0 is the specials bucket. "Season 0 Episode 3" shows a viewer an
    // implementation detail.
    @Test
    fun seasonZeroReadsAsExtras() {
        val result = text(
            TvChromeItem(title = "Behind the Scenes", show = "Doctor Who", season = 0, episode = 3),
        )

        assertEquals("Extras E3 • Behind the Scenes", result.secondary)
    }

    @Test
    fun anItemWithNoSeasonShowsOnlyTheEpisode() {
        val result = text(
            TvChromeItem(title = "Part One", show = "A Miniseries", episode = 1),
        )

        assertEquals("E1 • Part One", result.secondary)
    }

    @Test
    fun anEpisodelessItemHasNoSecondLine() {
        val result = text(TvChromeItem(title = "A Special", show = "Doctor Who"))

        assertEquals("Doctor Who", result.primary)
        assertFalse(result.showsSecondary)
    }

    @Test
    fun nothingLoadedIsBlankRatherThanBroken() {
        val result = text(null)

        assertEquals("", result.primary)
        assertFalse(result.showsSecondary)
    }

    // Whitespace-only fields are the same as absent, which is what trim() in
    // the web's version is doing.
    @Test
    fun whitespaceCountsAsAbsent() {
        val result = text(TvChromeItem(title = "  Sintel  ", show = "   "))

        assertEquals("Sintel", result.primary)
        assertFalse(result.showsSecondary)
    }
}
