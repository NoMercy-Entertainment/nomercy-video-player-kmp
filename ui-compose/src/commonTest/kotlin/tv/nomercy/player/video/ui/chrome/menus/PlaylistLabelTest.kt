// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import tv.nomercy.player.video.tv.TvChromeItem
import kotlin.test.Test
import kotlin.test.assertEquals

// What the pane and its cards are called, against `renderPlaylistPane` and
// `buildPlaylistCard`.
//
// These read as cosmetic and are the labels a viewer navigates by. "S0: E4" on a
// bucket of specials and "Episodes" over a shelf of films are both wrong in a way
// that looks like the player not knowing what it is playing.
class PlaylistLabelTest {

    private val strings = MenuStrings()

    @Test
    fun aRunOfTelevisionIsHeadedEpisodes() {
        val queue: List<TvChromeItem> = listOf(
            TvChromeItem(title = "Pilot", season = 1, episode = 1),
            TvChromeItem(title = "Two", season = 1, episode = 2),
        )

        assertEquals(strings.episodes, playlistTitle(strings, queue))
    }

    @Test
    fun aPlaylistTypeOfTvIsEnoughOnItsOwn() {
        // The web tests `playlist_type === 'tv'` first, so a queue that has not
        // been given episode numbers is still a run of episodes.
        val queue: List<TvChromeItem> = listOf(TvChromeItem(title = "One", playlistType = "tv"))

        assertEquals(strings.episodes, playlistTitle(strings, queue))
    }

    @Test
    fun aShelfOfFilmsIsHeadedPlaylist() {
        val queue: List<TvChromeItem> = listOf(
            TvChromeItem(title = "One", videoType = "movie", episode = 1),
            TvChromeItem(title = "Two", videoType = "movie", episode = 2),
        )

        assertEquals(strings.playlist, playlistTitle(strings, queue))
    }

    @Test
    fun anEpisodeOfASeasonReadsBothTokens() {
        val item = TvChromeItem(title = "Pilot", season = 1, episode = 4)

        assertEquals("S1: E4", episodeToken(strings, item, index = 0))
    }

    @Test
    fun anEntryWithNoSeasonAtAllReadsTheEpisodeAlone() {
        // A collection numbers its entries without seasoning them, and the web's
        // second branch is exactly `typeof season !== 'number'`.
        val item = TvChromeItem(title = "Second film", episode = 2)

        assertEquals("E2", episodeToken(strings, item, index = 1))
    }

    @Test
    fun aSpecialFallsBackToItsPositionRatherThanReadingSeasonZero() {
        // Season 0 is the specials bucket. "S0: E4" is a database row, not a
        // label, which is why the web's `season >= 1` guard exists.
        val item = TvChromeItem(title = "Behind the scenes", season = 0, episode = 4)

        assertEquals("3", episodeToken(strings, item, index = 2))
    }

    @Test
    fun anItemWithNoEpisodeNumberReadsItsPosition() {
        assertEquals("1", episodeToken(strings, TvChromeItem(title = "A film"), index = 0))
    }

    @Test
    fun theSeasonRowFillsTheTranslationsOwnPlaceholder() {
        // `t('menu.season', { number })`. The template travels with the language
        // so a locale that puts the number first says so in its own string.
        assertEquals("Season 3", seasonLabel(strings, season = 3))
    }
}
