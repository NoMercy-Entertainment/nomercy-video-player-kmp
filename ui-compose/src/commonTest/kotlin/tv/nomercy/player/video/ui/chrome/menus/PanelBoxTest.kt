// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.tv.TvChromeItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The card's size, against the rule the stylesheet states.
//
// The port had one number for every case — 256dp, which is the web's
// `min-width: 16rem` and the FLOOR of a `width: min-content` card. On a 4K
// television the settings list was a 256px column in the corner of a 3840px
// picture, and the playlist pane had 256px to fit a 16rem seasons rail beside a
// 36rem episode rail: both were drawn and the second had no width left.
class PanelBoxTest {

    private val movie = TvChromeItem(title = "A film", videoType = "movie")
    private val seasonOne = TvChromeItem(title = "Pilot", season = 1, episode = 1)
    private val seasonTwo = TvChromeItem(title = "Return", season = 2, episode = 1)

    @Test
    fun anOrdinaryPaneAsksForTheWebsMinimumAndNoMore() {
        // `.main-menu { min-width: 16rem }` with nothing inside demanding width.
        // A track list on a wide player is still a 256dp column, which is the
        // whole reason the old constant looked right.
        assertEquals(256.dp, widthOf(MenuState.Audio, listOf(movie), room = 1920.dp))
    }

    @Test
    fun theSeasonalPlaylistAsksForBothRails() {
        // 16rem of seasons plus 36rem of episodes, which is the 52rem the frame
        // caps at — the pane the old 256 could not draw.
        assertEquals(
            832.dp,
            widthOf(MenuState.Playlist, listOf(seasonOne, seasonTwo), room = 1920.dp),
        )
    }

    @Test
    fun aFlatPlaylistAsksOnlyForTheEpisodeRail() {
        // `.playlist-flat .seasons-pane { display: none }` — one season needs no
        // rail, so asking for its 16rem would be asking for room nothing fills.
        assertEquals(576.dp, widthOf(MenuState.Playlist, listOf(seasonOne), room = 1920.dp))
    }

    @Test
    fun aCollectionOfFilmsIsFlatEvenWithSeasonNumbersOnIt() {
        // shouldShowSeasonSidebar excludes movies, and this reads its answer
        // rather than counting distinct seasons again.
        val films: List<TvChromeItem> = listOf(
            TvChromeItem(title = "One", season = 1, videoType = "movie"),
            TvChromeItem(title = "Two", season = 2, videoType = "movie"),
        )

        assertEquals(576.dp, widthOf(MenuState.Playlist, films, room = 1920.dp))
    }

    @Test
    fun aNarrowerPlayerClampsTheCardToItsOwnRoom() {
        // `max-width: calc(100% - 2rem)` — 16px of clearance at each edge, so a
        // 700dp player gives the card 668 rather than the 832 it asked for.
        assertEquals(
            668.dp,
            widthOf(MenuState.Playlist, listOf(seasonOne, seasonTwo), room = 700.dp),
        )
    }

    @Test
    fun theCapNeverExceedsFiftyTwoRemHoweverWideThePlayerIs() {
        // `min(52rem, …)`. A cinema-width player does not get a cinema-width menu.
        assertEquals(
            832.dp,
            widthOf(MenuState.Playlist, listOf(seasonOne, seasonTwo), room = 3840.dp),
        )
    }

    @Test
    fun theFloorBeatsTheClampOnAPlayerTooNarrowForEither() {
        // CSS resolves `min-width` after `max-width`, so 16rem wins and the card
        // is clipped rather than becoming a 168dp sliver nobody can read.
        assertEquals(256.dp, widthOf(MenuState.Audio, listOf(movie), room = 200.dp))
    }

    @Test
    fun anUnboundedHeightGetsNoCeilingAtAll() {
        // `room * 0.6` on an infinity is an infinity, and a scrolling pane handed
        // an infinite ceiling does not degrade — it throws, and the whole menu
        // goes with it. Which is any test harness, and any parent that scrolls.
        assertNull(panelBoxOf(MenuState.Main, listOf(movie), 1920.dp, Dp.Infinity).maxHeight)
    }

    @Test
    fun aBoundedHeightIsTheFramesOwnRuleAndNotTheListsShare() {
        // `.menu-frame { max-height: calc(100% - 2rem) }`.
        //
        // This asserted 60% of the player, citing `.main-menu` — which is a
        // different selector with a different rule. Applying the inner list's
        // share to the frame made every card, the playlist pane included, forty
        // per cent shorter than the browser's. The share lives in MainMenu.kt
        // now, on the list it belongs to.
        assertEquals(968.dp, panelBoxOf(MenuState.Main, listOf(movie), 1920.dp, 1000.dp).maxHeight)
    }

    @Test
    fun theFrameGivesUpItsInsetsEvenOnAPlayerTooShortForThem() {
        // 50dp of player, 32 of inset, 18 left. The frame does not go negative
        // and does not refuse to draw.
        assertEquals(18.dp, panelBoxOf(MenuState.Main, listOf(movie), 1920.dp, 50.dp).maxHeight)
    }

    @Test
    fun thePortraitPlaylistFillsThePlayerEdgeToEdge() {
        // `[data-orientation='portrait'] .menu-frame:has(.playlist-menu.is-open)`
        // — inset 0, width 100%, height 100%, border-radius 0.
        val panel: PanelBox =
            panelBoxOf(MenuState.Playlist, listOf(seasonOne, seasonTwo), 360.dp, 740.dp, portrait = true)

        assertEquals(360.dp, panel.width)
        assertEquals(740.dp, panel.maxHeight)
        assertEquals(true, panel.fullBleed)
        assertEquals(0.dp, panel.radius)
    }

    @Test
    fun portraitLeavesEveryOtherPaneThePopoverCard() {
        // The stylesheet's own comment: "Subtitle / audio / quality / speed /
        // aspect sub-menus keep their default popover sizing — they were fine."
        val panel: PanelBox = panelBoxOf(MenuState.Main, listOf(movie), 360.dp, 740.dp, portrait = true)

        assertEquals(256.dp, panel.width)
        assertEquals(false, panel.fullBleed)
    }

    @Test
    fun landscapeNeverTakesTheFullBleedBranch() {
        val panel: PanelBox =
            panelBoxOf(MenuState.Playlist, listOf(seasonOne, seasonTwo), 1920.dp, 1080.dp, portrait = false)

        assertEquals(false, panel.fullBleed)
        assertEquals(832.dp, panel.width)
    }

    @Test
    fun portraitIsHeightMeetingWidthAndNeverAnInfinity() {
        // The media query's own definition, and the unbounded-harness guard.
        assertEquals(true, isPortrait(360.dp, 740.dp))
        assertEquals(true, isPortrait(500.dp, 500.dp))
        assertEquals(false, isPortrait(1280.dp, 720.dp))
        assertEquals(false, isPortrait(360.dp, Dp.Infinity))
    }

    private fun widthOf(menu: MenuState, queue: List<TvChromeItem>, room: Dp): Dp =
        panelBoxOf(menu, queue, room, BOUNDED_HEIGHT).width

    private companion object {
        // Any finite height: every case here is about the width.
        val BOUNDED_HEIGHT = 1080.dp
    }
}
