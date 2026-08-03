// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import tv.nomercy.player.core.input.KeyCombo
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.input.VideoKeyHandlerPlugin

/**
 * Whether the keyboard reference is up, and how to change that.
 *
 * One value because two things need it: the key handler, which opens and closes it,
 * and the layer that draws it. Passed as a pair of arguments it pushed both the
 * assembly and the input builder past their limits, and it is one piece of state.
 */
public class ShortcutsPanel(
    public val open: Boolean,
    public val onToggle: () -> Unit,
    public val onDismiss: () -> Unit,
)

@Composable
internal fun rememberShortcutsPanel(): ShortcutsPanel {
    var open: Boolean by remember { mutableStateOf(false) }

    return ShortcutsPanel(open = open, onToggle = { open = !open }, onDismiss = { open = false })
}

/**
 * What the tap zones need, which is nothing the rest of this class uses.
 *
 * One value because it travels as one: the zones toggle rather than set —
 * playback, fullscreen, volume — so they read the state, write through the
 * commands, and stamp the seek run off the same clock.
 */
internal class TouchZoneInput(
    val state: ChromeState,
    val commands: ChromeCommands,
    val nowMs: () -> Long,
)

internal class ChromeInput(
    val keys: VideoKeyHandlerPlugin?,
    val controller: ChromeController,
    val zones: TouchZoneInput,
    val gestures: ChromeGestures?,
    /**
     * `?` — the web's `shortcuts.help`, and Escape to close it again.
     *
     * Handled here rather than in the key-handler plugin because the plugin acts on
     * the PLAYER and this opens a panel: nothing about the playback changes. It was
     * bound in neither, so a player with thirty-four shortcuts had no way to tell
     * anybody they existed.
     */
    val panel: ShortcutsPanel,
) {

    val pointerDriven: Boolean get() = keys != null

    fun pointerModifier(): Modifier =
        if (!pointerDriven) {
            Modifier
        } else {
            Modifier
                .focusable()
                .pointerActivity(controller::bumpActivity, controller::onPointerExit)
        }

    // The chrome wakes on any press it recognises and then the key does whatever
    // it is bound to. A press neither of them wants stays with the platform,
    // which is what leaves the window's own shortcuts working.
    fun keyModifier(): Modifier {
        val handler: VideoKeyHandlerPlugin = keys ?: return Modifier

        return Modifier.onKeyEvent { event -> handlePress(handler, keyComboOf(event)) }
    }

    private fun handlePress(handler: VideoKeyHandlerPlugin, combo: KeyCombo?): Boolean {
        if (combo == null) return false

        controller.bumpActivity()

        // Before the handler, because the handler would answer Escape by leaving
        // fullscreen while a panel is open over the picture.
        if (isShortcutsKey(combo)) {
            panel.onToggle()
            return true
        }

        return handler.handle(combo)
    }

    private fun isShortcutsKey(combo: KeyCombo): Boolean = isShortcutsCombo(combo, panel.open)
}


/**
 * The keyboard reference, with its own strings, drawn only when it is open.
 *
 * Gating inside rather than at the call site keeps the assembly one line shorter and
 * puts the condition next to the thing it conditions.
 */
@Composable
internal fun ShortcutsLayer(panel: ShortcutsPanel) {
    if (!panel.open) return

    val locale: String = rememberChromeLocale()

    ShortcutsOverlay(
        title = ChromeTranslations.get(locale, "plugin.desktop-ui.shortcuts.title"),
        groups = rememberShortcutGroups(locale),
        hint = ChromeTranslations.get(locale, "plugin.desktop-ui.shortcuts.hint"),
        onDismiss = panel.onDismiss,
    )
}

/**
 * Whether a press means "show me the shortcuts" or "close them".
 *
 * Its own function so it can be driven without a window. Every part of this that
 * could be wrong is a STRING — keyComboOf builds a canonical like `shift+?` out of
 * the event's code point, and a predicate that matched `slash` or `question` instead
 * would compile, read correctly, and never fire. That is not something the render
 * proves either: the overlay simply never appears and looks unimplemented.
 */
internal fun isShortcutsCombo(combo: KeyCombo, open: Boolean): Boolean =
    when (combo.canonical.lowercase()) {
        // `?` on a layout that has its own key for it, and Shift-and-slash on the
        // layouts that do not. Both are the web's `shortcuts.help`.
        "?", "shift+?", "shift+/" -> true
        // Only while it is up, so the same press leaves fullscreen otherwise.
        //
        // Two spellings for one key. `keyComboOf` asks `playerKeyOf` first, and
        // that maps a keyboard's Escape and a remote's back button to the same
        // [PlayerKey.Back] — so what actually arrives is "Back". Matching the
        // web's own spelling alone compiled, read correctly, and closed nothing.
        BACK, ESCAPE -> open
        else -> false
    }

private val BACK: String = PlayerKey.Back.combo.lowercase()

// The name a platform that does not route through playerKeyOf would produce.
private const val ESCAPE = "escape"
