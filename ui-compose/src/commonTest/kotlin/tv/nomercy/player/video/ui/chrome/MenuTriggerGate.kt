// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.ui.chrome.menus.MenuState
import tv.nomercy.player.video.ui.chrome.menus.RecordingMenuCommands
import tv.nomercy.player.video.ui.tv.TvChromeStrings
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `aria-expanded` on the bar's menu triggers, as menuControl.ts keeps it:
 * `setMenuTriggerExpanded` marks the trigger owning the open pane and
 * `collapseAllTriggers` clears every other one.
 *
 * In Compose the statement is which semantics action the node offers — an
 * expanded trigger can `collapse`, a collapsed one can `expand` — so these
 * assert the action set AND that each action does what it announces. A bar
 * that cannot see the pane fails all of them, which is what wires
 * `ChromeState.menu` in.
 */
@OptIn(ExperimentalTestApi::class)
abstract class MenuTriggerGate {

    private val commands = RecordingMenuCommands()

    private fun ComposeUiTest.mount(state: ChromeState, buttons: ChromeButtons = ChromeButtons()) {
        setContent {
            Box(modifier = Modifier.width(BAR_WIDTH.dp).height(BAR_HEIGHT.dp)) {
                TransportBar(state = state, commands = commands, strings = TvChromeStrings(), buttons = buttons)
            }
        }
        waitForIdle()
    }

    @Test
    fun aClosedSettingsTriggerOffersExpandAndNotCollapse() = runComposeUiTest {
        mount(ChromeState())

        onNodeWithTag(SETTINGS_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Collapse))
    }

    @Test
    fun anOpenMainPaneMarksTheSettingsTriggerExpanded() = runComposeUiTest {
        mount(ChromeState(menu = MenuState.Main))

        onNodeWithTag(SETTINGS_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Expand))
    }

    @Test
    fun theExpandActionOpensTheMenu() = runComposeUiTest {
        mount(ChromeState())

        onNodeWithTag(SETTINGS_TAG).performSemanticsAction(SemanticsActions.Expand)

        assertTrue("openSettingsMenu" in commands.calls, "expand must open the settings menu")
    }

    @Test
    fun pressingAnOpenTriggerClosesItsPane() = runComposeUiTest {
        // The pointer's half of the toggle, which the semantics actions above
        // already had and the click did not: onClick was `open` unconditionally,
        // so pressing the button that opened a pane re-opened the same pane and
        // the only way out was to click somewhere else.
        mount(ChromeState(menu = MenuState.Main))

        onNodeWithTag(SETTINGS_TAG).performClick()

        assertTrue("closeMenu" in commands.calls, "pressing an open trigger must close its pane")
    }

    @Test
    fun pressingAClosedTriggerStillOpensIt() = runComposeUiTest {
        mount(ChromeState())

        onNodeWithTag(SETTINGS_TAG).performClick()

        assertTrue("openSettingsMenu" in commands.calls, "pressing a closed trigger must open it")
    }

    @Test
    fun theCollapseActionClosesIt() = runComposeUiTest {
        mount(ChromeState(menu = MenuState.Main))

        onNodeWithTag(SETTINGS_TAG).performSemanticsAction(SemanticsActions.Collapse)

        assertTrue("closeMenu" in commands.calls, "collapse must close the menu")
    }

    @Test
    fun eachTriggerOwnsItsOwnPane() = runComposeUiTest {
        // The playlist pane marks the playlist trigger and leaves settings
        // collapsed — `menuTriggerBtn`'s map, not one flag for the whole bar.
        mount(
            ChromeState(
                menu = MenuState.Playlist,
                queue = QUEUE,
                queueSize = QUEUE.size,
            ),
            // Off the default bar — the consumer asks for it, as the web's
            // button map has it.
            buttons = ChromeButtons(playlist = true),
        )

        onNodeWithTag(PLAYLIST_TAG).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
        onNodeWithTag(SETTINGS_TAG).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
    }
}

// Room for the whole default bar plus the playlist trigger.
private const val BAR_WIDTH = 1280
private const val BAR_HEIGHT = 56

private val QUEUE: List<TvChromeItem> = listOf(
    TvChromeItem(id = "a", title = "One"),
    TvChromeItem(id = "b", title = "Two"),
)
