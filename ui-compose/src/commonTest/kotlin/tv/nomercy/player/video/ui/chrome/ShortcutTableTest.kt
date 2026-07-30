// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// The keyboard reference, against the web's own table.
//
// buildShortcutsOverlay had nothing on this side at all. The key handler bound the
// keys and there was no way to find out they existed — a player with thirty-four
// shortcuts and no list of them has thirty-four secrets.
class ShortcutTableTest {

    private val groups: List<ShortcutGroup> = shortcutGroups("en")

    @Test
    fun everyGroupTheWebListsIsHere() {
        assertEquals(WEB_GROUPS, groups.size)
    }

    @Test
    fun everyShortcutTheWebListsIsHere() {
        assertEquals(WEB_ENTRIES, groups.sumOf { it.entries.size })
    }

    @Test
    fun aChordKeepsItsModifierSeparate() {
        // `{ keys: ['Shift', '← / →'] }`. Flattened to one string the overlay cannot
        // put a `+` between them, and which part is the modifier stops being stated.
        val chords: List<ShortcutEntry> = groups.flatMap { it.entries }.filter { it.keys.size > 1 }

        assertTrue(chords.isNotEmpty(), "no chord survived — the modifiers were flattened")
        assertTrue(chords.any { it.keys.first() == "Shift" })
        assertTrue(chords.any { it.keys.first() == "Ctrl" })
        assertTrue(chords.any { it.keys.first() == "Alt" })
    }

    @Test
    fun theLabelsComeFromTheLocaleTableAndTheKeysDoNot() {
        val dutch: List<ShortcutGroup> = shortcutGroups("nl")

        // Every label translated…
        assertNotEquals(groups.first().title, dutch.first().title, "group titles are not translated")
        assertNotEquals(
            groups.first().entries.first().label,
            dutch.first().entries.first().label,
            "shortcut labels are not translated",
        )

        // …and no key touched. A Dutch keyboard still has Space on it.
        assertEquals(
            groups.flatMap { group -> group.entries.map { it.keys } },
            dutch.flatMap { group -> group.entries.map { it.keys } },
        )
    }

    @Test
    fun nothingIsLeftUntranslated() {
        // A missing key comes back as the key itself, which reads as a bug on screen
        // rather than as an absence. Nine groups times an average of four is a lot of
        // places for one typo to hide.
        val untranslated: List<String> = groups
            .flatMap { group -> listOf(group.title) + group.entries.map { it.label } }
            .filter { it.startsWith("plugin.desktop-ui.") }

        assertEquals(emptyList(), untranslated)
    }
}

// Counted off desktop-ui/helpers/dom.ts by grepping it, not by adding up the groups
// by hand — the first attempt at that said 34 and the file says 32.
//
// check-chrome-parity.py counts the same two things out of the web source on every
// run, so this pair going stale is red there rather than silently agreeing with a
// table that lost a row.
private const val WEB_GROUPS = 8
private const val WEB_ENTRIES = 32
