// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

// The keyboard half of the menu, ported from `wireMenuKeyboardNav` and the
// dialog's own focus rules.
//
// The web gets three of the four behaviours from the platform: `dialog.show()`
// moves focus onto the first button of the visible pane and remembers what was
// focused before, `dialog.close()` gives focus back to it, and Enter or Space on
// a focused `<button>` is a click. The one piece it writes itself is the arrow
// walk — Up and Down through the pane's visible buttons, wrapping at both ends.
// Compose provides none of those on a desktop window, so all four live here.

/** Which pane is up, and where its rows are — the arrow walk's whole world. */
internal class MenuNav {

    /**
     * The pane's first button, which is where `dialog.show()` lands focus: the
     * back arrow on a sub-pane, the close cross on the main list. MenuHeader
     * attaches it; SettingsPanel requests it when the pane comes up.
     */
    val header: FocusRequester = FocusRequester()

    private val entries: MutableList<MenuNavEntry> = mutableListOf()
    private var focused: MenuNavEntry? = null

    fun register(entry: MenuNavEntry) {
        entries += entry
    }

    fun unregister(entry: MenuNavEntry) {
        entries -= entry
        if (focused == entry) focused = null
    }

    fun onFocus(entry: MenuNavEntry, isFocused: Boolean) {
        if (isFocused) {
            focused = entry
        } else if (focused == entry) {
            focused = null
        }
    }

    /**
     * One arrow press. False when there is nothing to walk, which the web also
     * answers by NOT calling preventDefault — the press falls through to the
     * player's own bindings rather than dying on an empty list.
     */
    fun move(down: Boolean): Boolean {
        // Screen order rather than registration order, because the rows of a
        // pane register from several composables and the web's own list is
        // `querySelectorAll` — document order, which on one column is top to
        // bottom.
        val rows: List<MenuNavEntry> = entries.sortedWith(compareBy({ it.position.y }, { it.position.x }))
        if (rows.isEmpty()) return false

        val target: MenuNavEntry = rows[menuNavTarget(rows.indexOf(focused), rows.size, down)]
        runCatching { target.requester.requestFocus() }
        return true
    }

    fun focusHeader() {
        runCatching { header.requestFocus() }
    }
}

/** One button the arrows can land on: its requester and where it sits. */
internal class MenuNavEntry {
    val requester: FocusRequester = FocusRequester()
    var position: Offset = Offset.Zero
}

/**
 * The web's own arithmetic, digits for digits:
 *
 *     const nextIdx = key === 'ArrowDown'
 *         ? (activeIdx + 1) % rows.length
 *         : (activeIdx - 1 + rows.length) % rows.length;
 *
 * [index] is -1 when nothing in the list has focus, exactly as `indexOf` answers
 * it there — so Down from nowhere lands on the first row, and Up from nowhere
 * lands one short of the end, because that is what the modulo does with -2.
 * Cleverer answers exist; this is the one a viewer of both players gets.
 */
internal fun menuNavTarget(index: Int, count: Int, down: Boolean): Int =
    if (down) (index + 1) % count else (index - 1 + count) % count

/**
 * What the open pane does with a key, tried BEFORE the player's own handler —
 * the web stops propagation for the same reason: "the player's seek/volume key
 * handlers cannot fire while a menu is open".
 *
 * Escape closes the whole menu, not one level — `closeAllMenus`, not
 * `backToMain`. Left and Right deliberately fall through: the web's nav binds
 * only Up and Down, and an open menu there does not stop a seek.
 */
internal fun MenuNav.onMenuKey(event: KeyEvent, onClose: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    return when (event.key) {
        Key.DirectionDown -> move(down = true)
        Key.DirectionUp -> move(down = false)
        Key.Escape -> {
            onClose()
            true
        }
        else -> false
    }
}

/**
 * Puts a row on the arrow walk. Applied by MenuRow, the playlist card and the
 * header's two buttons — every `<button>` the web's `querySelectorAll` finds.
 * Outside a menu ([nav] null) it is nothing, so the same row widget costs
 * nothing wherever else it is drawn.
 */
@Composable
internal fun Modifier.menuNavEntry(nav: MenuNav?): Modifier {
    if (nav == null) return this

    val entry: MenuNavEntry = remember(nav) { MenuNavEntry() }
    DisposableEffect(nav, entry) {
        nav.register(entry)
        onDispose { nav.unregister(entry) }
    }

    return this
        .focusRequester(entry.requester)
        .onGloballyPositioned { entry.position = it.positionInRoot() }
        .onFocusChanged { nav.onFocus(entry, it.isFocused) }
}

/**
 * `dialog.close()`'s other half: "set this's previously focused element to the
 * focused element" on show, and focus it again on close. The trigger arms this
 * as it opens the menu; the panel restores it as it leaves the tree — whichever
 * way it left: Escape, the close cross, or a row that picked something.
 */
internal class MenuReturnFocus {
    private var target: FocusRequester? = null

    fun arm(requester: FocusRequester) {
        target = requester
    }

    fun restore() {
        runCatching { target?.requestFocus() }
    }
}

internal val LocalMenuNav = compositionLocalOf<MenuNav?> { null }

/**
 * Whether a keyboard is driving this chrome. Focus is moved INTO the menu on
 * open only then: the browser does move focus on a touch phone too, but hides
 * the ring behind `:focus-visible` — and this chrome draws focus as a filled
 * button, so moving it under a finger would light the close cross on every tap
 * of the settings button.
 */
internal val LocalMenuKeyboard = compositionLocalOf { false }

internal val LocalMenuReturnFocus = compositionLocalOf { MenuReturnFocus() }
