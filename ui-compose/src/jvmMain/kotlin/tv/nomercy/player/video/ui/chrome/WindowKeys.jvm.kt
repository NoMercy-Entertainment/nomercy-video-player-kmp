// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import tv.nomercy.player.core.input.KeyCombo
import tv.nomercy.player.core.input.keyCombo
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * The window's keys, through AWT's focus manager.
 *
 * A dispatcher registered here sees every press in the application before it is
 * routed to whichever component holds focus, which is what the reference gets
 * from a listener on the document. Compose's own `Modifier.onKeyEvent` cannot:
 * it is a node in the focus tree and only ever sees what reaches it.
 */
@Composable
internal actual fun WindowKeyEvents(enabled: Boolean, onKey: (KeyCombo) -> Boolean) {
    // Read through a State, so the dispatcher registers once and still calls the
    // current lambda. Keying the effect on `onKey` would re-register on every
    // recomposition, and this chrome recomposes on the playhead.
    val current: State<(KeyCombo) -> Boolean> = rememberUpdatedState(onKey)

    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }

        val dispatcher = KeyEventDispatcher { event ->
            // Presses only. AWT reports pressed, released and typed for one
            // stroke, and acting on all three runs a binding three times — one
            // tap on the space bar toggling playback back to where it started.
            if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            awtKeyCombo(event)?.let(current.value) ?: false
        }

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
        onDispose {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
        }
    }
}

/**
 * An AWT press as the binding table spells it.
 *
 * The table is keyed the way the reference keys it — against
 * `KeyboardEvent.key`, so `ArrowLeft` and `a` rather than VK constants.
 */
private fun awtKeyCombo(event: KeyEvent): KeyCombo? {
    val key: String = when (event.keyCode) {
        KeyEvent.VK_SPACE -> " "
        KeyEvent.VK_LEFT -> "ArrowLeft"
        KeyEvent.VK_RIGHT -> "ArrowRight"
        KeyEvent.VK_UP -> "ArrowUp"
        KeyEvent.VK_DOWN -> "ArrowDown"
        KeyEvent.VK_ENTER -> "Enter"
        KeyEvent.VK_ESCAPE -> "Escape"
        KeyEvent.VK_HOME -> "Home"
        KeyEvent.VK_END -> "End"
        KeyEvent.VK_PAGE_UP -> "PageUp"
        KeyEvent.VK_PAGE_DOWN -> "PageDown"
        KeyEvent.VK_TAB -> "Tab"
        KeyEvent.VK_BACK_SPACE -> "Backspace"
        else -> {
            val typed: Char = event.keyChar
            // A press with no printable character is a modifier being held on
            // its own, or a key this table has no name for. Claiming it would
            // swallow the window's own shortcuts.
            if (typed == KeyEvent.CHAR_UNDEFINED || typed.code < ' '.code) return null
            typed.lowercaseChar().toString()
        }
    }

    return keyCombo(
        key = key,
        shift = event.isShiftDown,
        ctrl = event.isControlDown,
        alt = event.isAltDown,
        meta = event.isMetaDown,
    )
}
