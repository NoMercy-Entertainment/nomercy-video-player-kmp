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

// The two lines across the top of a television, and the branching behind them.
//
// This is where an extraction goes wrong quietly. None of these failures crash:
// they produce "S0E4" on a collection, or a blank line where an episode number
// belonged, and nobody notices until somebody is looking at their own library.
class TvChromeItemTest {

    @Test
    fun aFilmHasNoSecondLine() {
        // Its name is already across the top, and repeating it underneath is a
        // stutter rather than information.
        val film = TvChromeItem(title = "Rail Wars")

        assertEquals("", episodeLabel(film))
    }

    @Test
    fun anEpisodeIsNumberedTheWayEverybodyWritesIt() {
        val episode = TvChromeItem(title = "The Bridge", show = "Rail Wars", season = 2, episode = 3)

        assertEquals("S2E3 • The Bridge", episodeLabel(episode))
    }

    @Test
    fun seasonZeroIsCalledWhatALibraryPutsThere() {
        // Nothing in a library labels itself season zero to a viewer. It is
        // where everything outside the run itself is filed.
        val extra = TvChromeItem(title = "Behind the Scenes", show = "Rail Wars", season = 0, episode = 4)

        assertEquals("Extras E4 • Behind the Scenes", episodeLabel(extra))
    }

    @Test
    fun aCollectionIsNumberedWithoutInventingASeason() {
        // The failure this exists to stop. Numbering a collection like a series
        // produces a season that does not exist.
        val entry = TvChromeItem(
            title = "The First One",
            show = "A Collection",
            episode = 1,
            playlistType = "collection",
        )

        assertEquals("1 • The First One", episodeLabel(entry))
    }

    @Test
    fun aSpecialIsTreatedTheSameWhicheverFieldSaysSo() {
        // The two sources disagree: a playlist says what kind of list it is and
        // an item says what kind of thing it is. A special arrives labelled
        // either way and has to read the same both times.
        val byPlaylist = TvChromeItem(title = "A Special", show = "Rail Wars", episode = 2, playlistType = "special")
        val byVideo = TvChromeItem(title = "A Special", show = "Rail Wars", episode = 2, videoType = "special")

        assertEquals(episodeLabel(byPlaylist), episodeLabel(byVideo))
        assertEquals("2 • A Special", episodeLabel(byVideo))
    }

    @Test
    fun somethingWithAPositionButNoShowNameGetsNoSecondLine() {
        // The rule the reference states and the only case that distinguishes it:
        // a numbered entry with nothing to be numbered within. Without the guard
        // a lone film in a collection gains a "1 •" prefix from a list it is not
        // being shown as part of.
        val orphan = TvChromeItem(title = "The First One", episode = 1, playlistType = "collection")

        assertEquals("", episodeLabel(orphan))
    }

    @Test
    fun anEpisodeWithNoTitleYetStillShowsItsNumber() {
        // Titles arrive with metadata and the number is known from the file. A
        // blank line while it loads is worse than a number on its own.
        val loading = TvChromeItem(show = "Rail Wars", season = 1, episode = 5)

        assertEquals("S1E5", episodeLabel(loading))
    }

    @Test
    fun somethingWithNoEpisodeNumberSaysNothingRatherThanGuessing() {
        val vague = TvChromeItem(title = "A Thing", show = "Rail Wars")

        assertEquals("", episodeLabel(vague))
    }

    @Test
    fun theLabelsAreSuppliedBecauseTheyAreNotEnglishEverywhere() {
        // The shortest strings in the product and the likeliest to differ. A
        // season is not abbreviated to S in every language.
        val episode = TvChromeItem(title = "The Bridge", show = "Rail Wars", season = 2, episode = 3)

        val dutch = episodeLabel(episode, EpisodeLabels(season = "Se", episode = "Af", extras = "Extra"))

        assertEquals("Se2Af3 • The Bridge", dutch)
    }

    @Test
    fun theTitleAcrossTheTopIsTheShowWhereThereIsOne() {
        val episode = TvChromeItem(title = "The Bridge", show = "Rail Wars", season = 2, episode = 3)

        assertEquals("Rail Wars", showTitle(episode, loading = "Loading"))
    }

    @Test
    fun aFilmPutsItsOwnNameAcrossTheTop() {
        assertEquals("Rail Wars", showTitle(TvChromeItem(title = "Rail Wars"), loading = "Loading"))
    }

    @Test
    fun withNothingLoadedYetItSaysSo() {
        assertEquals("Loading", showTitle(null, loading = "Loading"))
        assertEquals("Loading", showTitle(TvChromeItem(), loading = "Loading"))
    }
}
