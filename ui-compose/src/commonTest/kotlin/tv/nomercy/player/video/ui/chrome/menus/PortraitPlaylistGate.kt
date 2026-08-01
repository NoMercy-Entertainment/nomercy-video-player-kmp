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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.ui.chrome.ChromeState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The portrait playlist, measured on screen against styles.css 1060-1151.
 *
 * The block's own comments state the two rules a source-reading check gets
 * wrong: the full-viewport frame applies ONLY while the playlist pane is the
 * open one, and only in portrait. So this mounts the real menu at portrait and
 * landscape sizes and asks where things ended up — the card's edges, the order
 * of the rails, the thumbnail's measured share.
 */
@OptIn(ExperimentalTestApi::class)
abstract class PortraitPlaylistGate {

    private val commands = RecordingMenuCommands()
    private var menu: MenuState = MenuState.Playlist

    private fun ComposeUiTest.open(width: Int, height: Int, start: MenuState, queue: List<TvChromeItem>) {
        menu = start
        setContent {
            Box(modifier = Modifier.width(width.dp).height(height.dp)) {
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
    fun inPortraitThePlaylistCardFillsTheWholePlayer() = runComposeUiTest {
        // `top: 0; right: 0; left: 0; bottom: 0; width: 100%; height: 100%` —
        // no 16px insets, no 52px bar clearance.
        open(PORTRAIT_W, PORTRAIT_H, MenuState.Playlist, TWO_SEASONS)

        val card: DpRect = onNodeWithTag(SETTINGS_MENU_TAG).getBoundsInRoot()
        assertNear(0.dp, card.left, "left inset")
        assertNear(0.dp, card.top, "top inset")
        assertNear(PORTRAIT_W.dp, card.width, "card width")
        assertNear(PORTRAIT_H.dp, card.height, "card height")
    }

    @Test
    fun inPortraitTheRailsStack() = runComposeUiTest {
        // `.playlist-cols { flex-direction: column }` — seasons above episodes,
        // both full width, instead of side by side.
        open(PORTRAIT_W, PORTRAIT_H, MenuState.Playlist, TWO_SEASONS)

        val seasons: DpRect = onNodeWithTag(SEASONS_RAIL_TAG).getBoundsInRoot()
        val episodes: DpRect = onNodeWithTag(EPISODES_RAIL_TAG).getBoundsInRoot()

        assertTrue(seasons.bottom <= episodes.top, "seasons rail must sit above the episode rail")
        assertNear(PORTRAIT_W.dp, episodes.width, "episode rail width")
    }

    @Test
    fun landscapeKeepsTheSideBySideCard() = runComposeUiTest {
        // The fork must not leak: at 1280x720 the card is still the 52rem
        // popover with the rails beside each other.
        open(LANDSCAPE_W, LANDSCAPE_H, MenuState.Playlist, TWO_SEASONS)

        val card: DpRect = onNodeWithTag(SETTINGS_MENU_TAG).getBoundsInRoot()
        assertNear(CARD_52REM.dp, card.width, "landscape card width")

        val seasons: DpRect = onNodeWithTag(SEASONS_RAIL_TAG).getBoundsInRoot()
        val episodes: DpRect = onNodeWithTag(EPISODES_RAIL_TAG).getBoundsInRoot()
        assertTrue(seasons.right <= episodes.left, "landscape rails must sit side by side")
    }

    @Test
    fun portraitLeavesEveryOtherPaneItsPopover() = runComposeUiTest {
        // "Subtitle / audio / quality / speed / aspect sub-menus keep their
        // default popover sizing — they were fine." — the block's own comment.
        open(PORTRAIT_W, PORTRAIT_H, MenuState.Main, TWO_SEASONS)

        val card: DpRect = onNodeWithTag(SETTINGS_MENU_TAG).getBoundsInRoot()
        assertNear(CARD_16REM.dp, card.width, "portrait main-menu card width")
    }

    @Test
    fun thePortraitThumbnailTakesThirtyEightPercentOfTheCard() = runComposeUiTest {
        // `width: 38%; flex-basis: 38%` — of the card's CONTENT BOX: the player
        // minus the rail's 6px sides and the card's 8px padding. The 10px gap is
        // not in that basis, because a CSS percentage resolves against the flex
        // container's content box and `gap` is spent between items afterwards.
        // Netting the gap out first put the expectation at 122.36 against a
        // measured 126 — the browser's own number is the one without it.
        open(PORTRAIT_W, PORTRAIT_H, MenuState.Playlist, ONE_SEASON)

        val content: Int = PORTRAIT_W - RAIL_SIDES - CARD_SIDES
        // Unmerged, because the card is clickable and therefore a merged
        // semantics node — its children are not separate nodes in the merged
        // tree, which is where the thumbnail lives.
        val thumb: DpRect = onNodeWithTag("${EPISODE_THUMB_TAG}0", useUnmergedTree = true).getBoundsInRoot()
        assertNear((content * THIRTY_EIGHT).dp, thumb.width, "thumbnail share")
    }

    @Test
    fun andNeverGrowsPastOneHundredEighty() = runComposeUiTest {
        // `max-width: 180px`. At 600dp across, 38% would be 217.
        open(WIDE_PORTRAIT_W, WIDE_PORTRAIT_H, MenuState.Playlist, ONE_SEASON)

        // Unmerged, because the card is clickable and therefore a merged
        // semantics node — its children are not separate nodes in the merged
        // tree, which is where the thumbnail lives.
        val thumb: DpRect = onNodeWithTag("${EPISODE_THUMB_TAG}0", useUnmergedTree = true).getBoundsInRoot()
        assertNear(THUMB_CAP.dp, thumb.width, "thumbnail cap")
    }

    private fun assertNear(expected: Dp, actual: Dp, what: String) {
        assertTrue(
            abs(expected.value - actual.value) <= TOLERANCE,
            "$what: expected ~$expected, measured $actual",
        )
    }
}

// A phone upright, a phone on its side, and a tablet upright wide enough to hit
// the thumbnail cap.
private const val PORTRAIT_W = 360
private const val PORTRAIT_H = 740
private const val WIDE_PORTRAIT_W = 600
private const val WIDE_PORTRAIT_H = 900
private const val LANDSCAPE_W = 1280
private const val LANDSCAPE_H = 720

// The landscape 52rem card and the 16rem popover floor, in dp.
private const val CARD_52REM = 832
private const val CARD_16REM = 256

// `.episode-menu .scroll-container { padding: 8px 6px }` twice over, and the
// card's own `padding: 8px` twice over.
private const val RAIL_SIDES = 12
private const val CARD_SIDES = 16
private const val THIRTY_EIGHT = 0.38f
private const val THUMB_CAP = 180
private const val TOLERANCE = 1.5f

private val TWO_SEASONS: List<TvChromeItem> = listOf(
    TvChromeItem(id = "s1e1", title = "Pilot", season = 1, episode = 1, durationSeconds = 2_700.0),
    TvChromeItem(id = "s1e2", title = "Second", season = 1, episode = 2, description = "More of it."),
    TvChromeItem(id = "s2e1", title = "Return", season = 2, episode = 1),
)

private val ONE_SEASON: List<TvChromeItem> = listOf(
    TvChromeItem(id = "a", title = "Pilot", season = 1, episode = 1, description = "It begins."),
    TvChromeItem(id = "b", title = "Second", season = 1, episode = 2),
)
