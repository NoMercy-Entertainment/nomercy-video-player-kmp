// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.ui.chrome.ChromeState
import tv.nomercy.player.video.ui.chrome.rememberMenuTrigger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The menu on a keyboard, against the web's own rules.
 *
 * Three of the four come from the `<dialog>` element there — `show()` focuses
 * the pane's first button, `close()` hands focus back to the opener, Enter on a
 * focused button is a click — and the fourth is `wireMenuKeyboardNav`'s arrow
 * walk. Compose provides none of them on a desktop window, so each is asserted
 * here and each fails if its wiring is removed.
 *
 * Desktop-only for the reason VideoChromeDesktopTest is: a phone has no
 * keyboard, and the touch chrome deliberately does not move focus on open.
 */
@OptIn(ExperimentalTestApi::class)
class MenuKeyboardDesktopTest {

    private val commands = RecordingMenuCommands()
    private var menu: MenuState by mutableStateOf(MenuState.Hidden)

    private fun ComposeUiTest.open(start: MenuState, state: ChromeState = ChromeState()) {
        menu = start
        setContent {
            CompositionLocalProvider(LocalMenuKeyboard provides true) {
                Box(Modifier.width(PLAYER_WIDTH.dp).height(PLAYER_HEIGHT.dp)) {
                    SettingsMenu(state, commands, menu, onMenuChange = { menu = it })
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun openingTheMainListFocusesItsCloseButton() = runComposeUiTest {
        // `dialog.show()` lands on the first focusable in the dialog, and the
        // main header has no back arrow — the close cross is first.
        open(MenuState.Main)

        onNodeWithTag(MENU_CLOSE_TAG).assertIsFocused()
    }

    @Test
    fun openingASubPaneFocusesItsBackArrow() = runComposeUiTest {
        open(MenuState.Subtitle)

        onNodeWithTag(MENU_BACK_TAG).assertIsFocused()
    }

    @Test
    fun arrowDownWalksFromTheHeaderIntoTheRows() = runComposeUiTest {
        open(MenuState.Main)

        onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        // The first row under the header. Audio and quality are absent on an
        // empty state — one track is not a menu — so subtitles leads the list.
        onNodeWithTag(ROW_SUBTITLE).assertIsFocused()
    }

    @Test
    fun arrowDownWrapsPastTheEndBackToTheTop() = runComposeUiTest {
        // The subtitle pane with no tracks is three buttons: back, close, Off.
        open(MenuState.Subtitle)

        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(ROW_SUBTITLE_OFF).assertIsFocused()

        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        onNodeWithTag(MENU_BACK_TAG).assertIsFocused()
    }

    @Test
    fun arrowUpFromTheTopWrapsToTheEnd() = runComposeUiTest {
        open(MenuState.Subtitle)

        onRoot().performKeyInput { pressKey(Key.DirectionUp) }

        // (0 - 1 + 3) % 3 — the web's modulo, not a clamp that stays put.
        onNodeWithTag(ROW_SUBTITLE_OFF).assertIsFocused()
    }

    @Test
    fun enterActivatesTheFocusedRow() = runComposeUiTest {
        open(MenuState.Main)

        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        onRoot().performKeyInput { pressKey(Key.Enter) }

        // The subtitles row opens its pane — native button activation, which a
        // keyboard cannot reach at all if the arrows never move focus.
        assertEquals(MenuState.Subtitle, menu)
    }

    @Test
    fun escapeClosesTheWholeMenuNotOneLevel() = runComposeUiTest {
        // From a sub-pane, because that is where "one level" would be the easy
        // wrong answer: the web's Escape is `closeAllMenus`, not `backToMain`.
        open(MenuState.Subtitle)

        onRoot().performKeyInput { pressKey(Key.Escape) }

        assertEquals(MenuState.Hidden, menu)
        onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun closingReturnsFocusToTheTriggerThatOpened() = runComposeUiTest {
        // `dialog.close()`: "if the dialog's previously focused element is not
        // null … focus it". The trigger armed itself on open; Escape must land
        // focus back on it, or a keyboard user's next press goes nowhere.
        val returnFocus = MenuReturnFocus()
        menu = MenuState.Hidden

        setContent {
            CompositionLocalProvider(
                LocalMenuKeyboard provides true,
                LocalMenuReturnFocus provides returnFocus,
            ) {
                Column {
                    val trigger = rememberMenuTrigger { menu = MenuState.Main }
                    Box(
                        Modifier
                            .size(TRIGGER_SIZE.dp)
                            .testTag(TRIGGER_TAG)
                            .focusRequester(trigger.requester)
                            .clickable(onClick = trigger.open),
                    )
                    Box(Modifier.width(PLAYER_WIDTH.dp).height(PLAYER_HEIGHT.dp)) {
                        SettingsMenu(ChromeState(), commands, menu, onMenuChange = { menu = it })
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithTag(TRIGGER_TAG).performClick()
        waitForIdle()
        onNodeWithTag(MENU_CLOSE_TAG).assertIsFocused()

        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertEquals(MenuState.Hidden, menu)
        onNodeWithTag(TRIGGER_TAG).assertIsFocused()
    }
}

private const val PLAYER_WIDTH = 1280
private const val PLAYER_HEIGHT = 720
private const val TRIGGER_SIZE = 40
private const val TRIGGER_TAG = "test-menu-trigger"
