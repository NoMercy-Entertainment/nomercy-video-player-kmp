// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.ui.chrome.ChromeState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That the pane DRAWS, which is the defect rather than the rule.
 *
 * `PanelBoxTest` grades the width rule and `PlaylistLabelTest` grades the labels,
 * and both would have passed against the pane this replaces: two Columns in a Row
 * with no weights, each holding rows that fill their width — so the seasons rail
 * took the card's whole 256dp and the episode rail measured zero. Nothing threw.
 * The header said "Playlist" over an empty card.
 *
 * So these mount the real menu at a real size and ask the screen. There is no way
 * to satisfy them except by both rails having room and the cards being in them.
 */
@OptIn(ExperimentalTestApi::class)
abstract class PlaylistPaneGate {

    private val commands = RecordingMenuCommands()
    private var menu: MenuState = MenuState.Playlist

    private fun ComposeUiTest.open(queue: List<TvChromeItem>) {
        menu = MenuState.Playlist
        setContent {
            // A fixed box rather than the host's own window, because the window
            // size differs per host and 52rem of card needs a known width to fit
            // in. Wide enough for both rails plus the frame's own two insets.
            Box(modifier = Modifier.width(PLAYER_WIDTH.dp).height(PLAYER_HEIGHT.dp)) {
                SettingsMenu(
                    ChromeState(queue = queue),
                    commands,
                    menu,
                    onMenuChange = { menu = it },
                )
            }
        }
        waitForIdle()
    }

    @Test
    fun aRunOfTwoSeasonsDrawsBothRails() = runComposeUiTest {
        open(TWO_SEASONS)

        onNodeWithTag(SEASONS_RAIL_TAG).assertIsDisplayed()
        onNodeWithTag(EPISODES_RAIL_TAG).assertIsDisplayed()
    }

    @Test
    fun andACardForEveryEpisodeOfTheSeasonItOpenedOn() = runComposeUiTest {
        // Opened on season 1 because nothing is playing, so its two episodes are
        // the ones on screen and season 2's is not. An episode rail with no width
        // draws none of them, which is what shipped.
        open(TWO_SEASONS)

        onNodeWithTag("${ROW_EPISODE}0").assertIsDisplayed()
        onNodeWithTag("${ROW_EPISODE}1").assertIsDisplayed()
        onNodeWithTag("${ROW_EPISODE}2").assertDoesNotExist()
    }

    @Test
    fun choosingASeasonFiltersTheEpisodeRailToIt() = runComposeUiTest {
        // The season buttons did nothing at all before: their click handler
        // re-opened the pane they were already in.
        open(TWO_SEASONS)

        onNodeWithTag("${ROW_SEASON}2").performClick()

        onNodeWithTag("${ROW_EPISODE}2").assertIsDisplayed()
        onNodeWithTag("${ROW_EPISODE}0").assertDoesNotExist()
    }

    @Test
    fun aSingleSeasonDrawsNoRailAndStillDrawsItsEpisodes() = runComposeUiTest {
        // `.playlist-flat .seasons-pane { display: none }`, and the episode rail
        // takes the full width rather than sharing it with an empty column.
        open(ONE_SEASON)

        onNodeWithTag(SEASONS_RAIL_TAG).assertDoesNotExist()
        onNodeWithTag("${ROW_EPISODE}0").assertIsDisplayed()
        onNodeWithTag("${ROW_EPISODE}1").assertIsDisplayed()
    }

    @Test
    fun choosingACardPlaysThatItemRatherThanItsPosition() = runComposeUiTest {
        // By id, so a queue reordered while the menu was open still plays the
        // episode somebody aimed at. The old rows played nothing whatsoever.
        open(TWO_SEASONS)

        onNodeWithTag("${ROW_EPISODE}1").performClick()

        assertEquals("s1e2", commands.lastPlayedItem)
    }

    @Test
    fun andClosesTheMenuBehindIt() = runComposeUiTest {
        open(TWO_SEASONS)

        onNodeWithTag("${ROW_EPISODE}1").performClick()

        assertEquals(MenuState.Hidden, menu)
    }
}

// Wide and tall enough for 52rem of card inside the frame's 16px insets, so a
// failure here is the pane rather than the box it was given.
private const val PLAYER_WIDTH = 1280
private const val PLAYER_HEIGHT = 720

private val TWO_SEASONS: List<TvChromeItem> = listOf(
    TvChromeItem(id = "s1e1", title = "Pilot", season = 1, episode = 1, durationSeconds = 2_700.0),
    TvChromeItem(id = "s1e2", title = "Second", season = 1, episode = 2, progressPercent = 40),
    TvChromeItem(id = "s2e1", title = "Return", season = 2, episode = 1),
)

private val ONE_SEASON: List<TvChromeItem> = listOf(
    TvChromeItem(id = "a", title = "Pilot", season = 1, episode = 1, description = "It begins."),
    TvChromeItem(id = "b", title = "Second", season = 1, episode = 2),
)
