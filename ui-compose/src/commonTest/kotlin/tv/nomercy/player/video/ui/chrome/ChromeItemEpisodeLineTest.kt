// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.video.item.VideoPlaylistItem
import tv.nomercy.player.video.item.WatchProgress
import tv.nomercy.player.video.tv.episodeLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The top bar's second line, from an episode that says it is one.
 *
 * `episodeLabel` reads `show` and `episode` off the chrome's item, the style has
 * three breakpoints written for it and a test tag of its own — and `chromeItemOf`
 * carried neither field across, so the line could not render for ANY item, from
 * any server, ever. It measured 0.104 of the container short of the browser's
 * top bar, which is what sent someone looking.
 *
 * A fixture that declared the fields would have passed while the adapter dropped
 * them, so this goes through `chromeItemOf` — the thing that was broken — rather
 * than constructing a TvChromeItem directly.
 */
class ChromeItemEpisodeLineTest {

    private data class Episode(
        override val id: String = "s01e04",
        override val title: String = "The Quiet Earth",
        override val url: String = "file://one.mkv",
        override val show: String? = "Rail Wars",
        override val season: Int? = 1,
        override val episode: Int? = 4,
        override val durationSeconds: Double? = 1_800.0,
        override val progress: WatchProgress? = null,
    ) : VideoPlaylistItem

    @Test
    fun anEpisodeGetsItsSecondLine() {
        assertEquals("S1E4 • The Quiet Earth", episodeLabel(chromeItemOf(Episode())))
    }

    @Test
    fun andAFilmDoesNot() {
        // The empty case, kept beside the one above because the line is
        // deliberately absent on a film — its name is already across the top,
        // and a blank second line reads as something that failed to load.
        assertEquals("", episodeLabel(chromeItemOf(ChromeTestItem())))
    }
}
