// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import tv.nomercy.player.core.input.KeyCombo
import tv.nomercy.player.core.input.keyCombo
import tv.nomercy.player.video.ui.tv.playerKeyOf

// A keyboard press, in the vocabulary the binding table is written in.
//
// The table is spelled the way the web spells it — " " for space, "m" for mute,
// "shift+n" for the next chapter — because the whole point of porting it is that
// the same keys do the same things in a browser and on a desktop. So the mapping
// has to arrive at those exact strings, and this is where a press becomes one.
//
// Separate from playerKeyOf, which answers a different question. That one names
// the presses a remote can send and returns null for everything else; a desktop
// keyboard is mostly everything else, which is why the television chrome could
// use it alone and this one cannot.
internal fun keyComboOf(event: KeyEvent): KeyCombo? {
    if (event.type != KeyEventType.KeyDown) return null

    val name: String = nameOf(event) ?: return null

    return keyCombo(
        key = name,
        shift = event.isShiftPressed,
        ctrl = event.isCtrlPressed,
        alt = event.isAltPressed,
        meta = event.isMetaPressed,
    )
}

// The named keys first. A remote's arrow and a keyboard's arrow are the same
// press and are already named once, so asking there keeps one list rather than
// two that drift.
private fun nameOf(event: KeyEvent): String? =
    playerKeyOf(event)?.combo ?: NAMED_KEYS[event.key] ?: characterOf(event)

// Lower case, because a binding written for shift and a letter spells the letter
// unshifted: the modifier is already in the combo, and "shift+N" and "shift+n"
// are two entries in a map where only one of them can ever fire.
private fun characterOf(event: KeyEvent): String? {
    val code: Int = event.utf16CodePoint
    if (code < SPACE_CODE_POINT) return null

    return code.toChar().lowercaseChar().toString()
}

private const val SPACE_CODE_POINT = 32

// The presses that carry no character.
//
// The function keys have none by nature. Space is here for a different reason
// and it is a finding: it does have one, and Compose reports it as undefined on
// a synthesised event, so a mapping that read the character alone worked from a
// real keyboard and did nothing under test. Naming it removes the difference
// rather than papering over the test.
private val NAMED_KEYS: Map<Key, String> = mapOf(
    Key.Spacebar to " ",
    Key.F1 to "F1",
    Key.F2 to "F2",
    Key.F3 to "F3",
    Key.F4 to "F4",
    Key.F5 to "F5",
    Key.F6 to "F6",
    Key.F7 to "F7",
    Key.F8 to "F8",
    Key.F9 to "F9",
    Key.F10 to "F10",
    Key.F11 to "F11",
    Key.F12 to "F12",
)
