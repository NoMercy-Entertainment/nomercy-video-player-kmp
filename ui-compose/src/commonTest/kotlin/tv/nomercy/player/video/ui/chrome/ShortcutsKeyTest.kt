// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.input.KeyCombo
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
    fun anOrdinaryPressIsLeftToThePlayer() {
        for (canonical in listOf("Space", "f", "m", "ArrowLeft", "shift+n", "/")) {
            assertFalse(
                isShortcutsCombo(KeyCombo(canonical), open = true),
                "$canonical was swallowed by the shortcuts panel",
            )
        }
    }
}
