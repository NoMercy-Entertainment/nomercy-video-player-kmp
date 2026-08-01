// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.input.KeyCombo
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.core.input.asCombo
import tv.nomercy.player.core.input.keyCombo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The press that opens the keyboard reference.
//
// Everything here that could be wrong is a STRING. keyComboOf builds a canonical
// like `shift+?` out of the event's UTF-16 code point, and a predicate matching
// `slash` or `question` instead would compile, read correctly and never fire — and
// a render does not catch that either: the overlay simply never appears and the
// whole thing looks unimplemented.
class ShortcutsKeyTest {

    @Test
    fun questionMarkOpensIt() {
        // The two spellings a keyboard produces: a layout with its own `?` key, and
        // one where it is Shift and slash.
        assertTrue(isShortcutsCombo(KeyCombo("?"), open = false))
        assertTrue(isShortcutsCombo(keyCombo("?", shift = true), open = false))
        assertTrue(isShortcutsCombo(keyCombo("/", shift = true), open = false))
    }

    @Test
    fun escapeClosesItOnlyWhileItIsUp() {
        // Otherwise Escape is the player's, and it leaves fullscreen. A panel that
        // swallowed Escape at all times would trap a viewer in fullscreen.
        assertTrue(isShortcutsCombo(KeyCombo("Escape"), open = true))
        assertFalse(isShortcutsCombo(KeyCombo("Escape"), open = false))
    }

    @Test
    fun andItIsTheSpellingTheRealPressArrivesWith() {
        // What `keyComboOf` actually produces for Escape is `Back` — it asks
        // `playerKeyOf` first, and a keyboard's Escape and a remote's back button
        // are one [PlayerKey] there. This predicate matched only the web's own
        // spelling, so pressing Escape over the open panel did nothing at all,
        // and the case above passed anyway because it built the combo by hand.
        //
        // KeyComboOfTest.escapeArrivesAsTheRemotesBackButton is the other half:
        // it drives a real key event and pins the string this expects.
        assertTrue(isShortcutsCombo(PlayerKey.Back.asCombo(), open = true))
        assertFalse(isShortcutsCombo(PlayerKey.Back.asCombo(), open = false))
    }

    @Test
    fun anOrdinaryPressIsLeftToThePlayer() {
        for (canonical in listOf("Space", "f", "m", "ArrowLeft", "shift+n", "/")) {
            assertFalse(
                isShortcutsCombo(KeyCombo(canonical), open = true),
                "$canonical was swallowed by the shortcuts panel",
            )
        }
    }
}
