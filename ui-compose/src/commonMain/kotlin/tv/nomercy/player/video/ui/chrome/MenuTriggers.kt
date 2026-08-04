// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import tv.nomercy.player.video.ui.chrome.menus.LocalMenuKeyboard
import tv.nomercy.player.video.ui.chrome.menus.LocalMenuReturnFocus
import tv.nomercy.player.video.ui.chrome.menus.MenuReturnFocus
import tv.nomercy.player.video.ui.tv.PlayerIconButton

// The bar buttons that open a menu, and what the web keeps true about them.
//
// menuControl.ts holds a refs bag of exactly these buttons for one purpose:
// `setMenuTriggerExpanded` writes `aria-expanded` onto whichever trigger owns
// the open pane and `collapseAllTriggers` clears the rest. In Compose the same
// statement is which of the two semantics actions the node offers: an expanded
// trigger can `collapse`, a collapsed one can `expand`, and a reader announces
// the state either way.
//
// The other thing a trigger owns is the way back: `dialog.show()` notes the
// focused element and `dialog.close()` returns focus to it. The trigger is that
// element, so opening arms MenuReturnFocus with its own requester.

/**
 * `aria-expanded`, in Compose's vocabulary: the collapse action is offered
 * exactly while the pane is open, the expand action exactly while it is not,
 * and each does what it announces.
 */
internal fun Modifier.menuTriggerSemantics(
    expanded: Boolean,
    open: () -> Unit,
    close: () -> Unit,
): Modifier = semantics {
    if (expanded) {
        collapse {
            close()
            true
        }
    } else {
        expand {
            open()
            true
        }
    }
}

/** One trigger's identity: where focus returns to, and an open that says so. */
internal class MenuTriggerRef(val requester: FocusRequester, val open: () -> Unit)

@Composable
internal fun rememberMenuTrigger(open: () -> Unit): MenuTriggerRef {
    val returnFocus: MenuReturnFocus = LocalMenuReturnFocus.current
    val requester: FocusRequester = remember { FocusRequester() }

    return remember(returnFocus, open) {
        MenuTriggerRef(requester) {
            returnFocus.arm(requester)
            open()
        }
    }
}

/** What one trigger button draws and answers — bundled so the bar's row
 *  functions stay within their length. */
internal class MenuTriggerSpec(
    val icon: ImageVector,
    val description: String,
    val expanded: Boolean,
    val open: () -> Unit,
    val tag: String? = null,
)

@Composable
internal fun MenuTriggerButton(spec: MenuTriggerSpec, close: () -> Unit) {
    val trigger: MenuTriggerRef = rememberMenuTrigger(spec.open)

    PlayerIconButton(
        icon = spec.icon,
        description = spec.description,
        onClick = trigger.open,
        focusRequester = trigger.requester,
        // `.btn.is-active` — a trigger whose pane is open is drawn the way a
        // trigger under the pointer is, which is what tells a viewer which of
        // the eighteen controls opened the card in front of them.
        active = spec.expanded,
        modifier = (spec.tag?.let { Modifier.testTag(it) } ?: Modifier)
            .menuTriggerSemantics(spec.expanded, trigger.open, close),
    )
}

// What the menus need to know about their surroundings: whether a keyboard is
// driving (focus moves into an opening pane only then), and where focus returns
// when a pane closes. One instance per chrome, so two players on one screen do
// not trade focus.
@Composable
internal fun ChromeMenuScope(keyboard: Boolean, menuOpen: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMenuKeyboard provides keyboard,
        LocalMenuOpen provides menuOpen,
        LocalMenuReturnFocus provides remember { MenuReturnFocus() },
        content = content,
    )
}

// Whether a pane is in front of the picture.
//
// A tooltip reads it and stays quiet. The web hides every tooltip while a menu
// is open, and this one did not: hovering the gear that opened the card painted
// its label ON TOP of the card's own rows, which photographs as a menu drawing
// over itself.
internal val LocalMenuOpen = staticCompositionLocalOf { false }
