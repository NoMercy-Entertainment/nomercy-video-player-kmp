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

// The web's rule, as cases rather than as a comment.
//
// Every one of these is a way the naive version ("show seasons if there is more
// than one") gets it wrong, and two of them are invisible in a screenshot: an
// empty rail beside a film collection and a one-button rail beside a run of
// specials both read as layout bugs to whoever finds them.
class SeasonSidebarTest {

    private fun episode(season: Int?, playlistType: String? = "tv", videoType: String? = "tv") =
        TvChromeItem(title = "e", season = season, playlistType = playlistType, videoType = videoType)

    private fun movie() =
        TvChromeItem(title = "m", season = null, playlistType = "movie", videoType = "movie")

    @Test
    fun twoSeasonsShowTheRail() {
        val queue = listOf(episode(1), episode(1), episode(2))

        assertTrue(shouldShowSeasonSidebar(queue))
        assertEquals(listOf(1, 2), sidebarSeasons(queue))
    }

    @Test
    fun oneSeasonIsFlat() {
        val queue = listOf(episode(1), episode(1), episode(1))

        assertFalse(shouldShowSeasonSidebar(queue))
        assertTrue(sidebarSeasons(queue).isEmpty())
    }

    // Season 0 is the specials bucket: a real season number, and not one a
    // viewer navigates by. A rail here would carry a single button labelled 0.
    @Test
    fun specialsAreFlatEvenWithARealSeasonBesideThem() {
        val queue = listOf(episode(0), episode(0), episode(1))

        assertFalse(shouldShowSeasonSidebar(queue))
    }

    @Test
    fun onlySpecialsAreFlat() {
        assertFalse(shouldShowSeasonSidebar(listOf(episode(0), episode(0))))
    }

    // A film collection has no seasons to show, and the naive rule draws an
    // empty rail beside it.
    @Test
    fun aMovieCollectionIsFlat() {
        assertFalse(shouldShowSeasonSidebar(listOf(movie(), movie(), movie())))
    }

    // Either field can carry the label, because the two sources disagree: a
    // playlist says what kind of list it is and an item says what kind of thing
    // it is. Checking only one lets a movie through as a season.
    @Test
    fun aMovieLabelledOnEitherFieldIsExcluded() {
        val byPlaylist = listOf(
            episode(1, playlistType = "movie", videoType = "tv"),
            episode(2, playlistType = "movie", videoType = "tv"),
        )
        val byVideo = listOf(
            episode(1, playlistType = "tv", videoType = "movie"),
            episode(2, playlistType = "tv", videoType = "movie"),
        )

        assertFalse(shouldShowSeasonSidebar(byPlaylist))
        assertFalse(shouldShowSeasonSidebar(byVideo))
    }

    @Test
    fun itemsWithNoSeasonAtAllAreIgnored() {
        val queue = listOf(episode(null), episode(null), episode(1))

        assertFalse(shouldShowSeasonSidebar(queue))
    }

    @Test
    fun anEmptyQueueIsFlat() {
        assertFalse(shouldShowSeasonSidebar(emptyList()))
        assertTrue(sidebarSeasons(emptyList()).isEmpty())
    }

    // Sorted, so a queue that arrived out of order does not produce a rail
    // reading 2, 1, 3.
    @Test
    fun theRailIsOrderedBySeasonRatherThanByArrival() {
        val queue = listOf(episode(3), episode(1), episode(2), episode(1))

        assertEquals(listOf(1, 2, 3), sidebarSeasons(queue))
    }
}
