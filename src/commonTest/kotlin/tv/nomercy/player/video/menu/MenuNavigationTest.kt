// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Three of the four rules here are absences rather than features, which is why
// they go missing in a port: nothing on screen says "this wraps" or "the
// player's keys are standing down".
class MenuNavigationTest {

    @Test
    fun downMovesAndWrapsAtTheEnd() {
        assertEquals(1, nextMenuRow(current = 0, rowCount = 3, down = true))
        assertEquals(2, nextMenuRow(current = 1, rowCount = 3, down = true))
        // A menu that stopped here makes a viewer press a key that does
        // nothing, which reads as the menu having frozen.
        assertEquals(0, nextMenuRow(current = 2, rowCount = 3, down = true))
    }

    @Test
    fun upMovesAndWrapsAtTheStart() {
        assertEquals(1, nextMenuRow(current = 2, rowCount = 3, down = false))
        assertEquals(0, nextMenuRow(current = 1, rowCount = 3, down = false))
        assertEquals(2, nextMenuRow(current = 0, rowCount = 3, down = false))
    }

    // -1 is "nothing focused yet". Down lands on the first row, which is what
    // anyone would expect.
    //
    // Up lands on the SECOND-TO-LAST, which nobody would. It falls out of the
    // web's own arithmetic — (-1 - 1 + len) % len — and it is kept rather than
    // corrected, because a viewer pressing Up into a fresh menu should land in
    // the same place on both. Recorded here so the next reader does not "fix"
    // it into a divergence.
    @Test
    fun nothingFocusedYetLandsWhereTheWebsArithmeticPutsIt() {
        assertEquals(0, nextMenuRow(current = -1, rowCount = 4, down = true))
        assertEquals(2, nextMenuRow(current = -1, rowCount = 4, down = false))
    }

    @Test
    fun anEmptyMenuDoesNotMove() {
        assertNull(nextMenuRow(current = -1, rowCount = 0, down = true))
        assertNull(nextMenuRow(current = 0, rowCount = 0, down = false))
    }

    // A row inside a collapsed pane is focusable and invisible. Focusing one
    // loses the highlight with nothing on screen to show where it went.
    @Test
    fun hiddenAndDisabledRowsAreSkipped() {
        data class Row(val id: String, val visible: Boolean, val enabled: Boolean)

        val rows = listOf(
            Row("english", visible = true, enabled = true),
            Row("dutch", visible = false, enabled = true),
            Row("commentary", visible = true, enabled = false),
            Row("off", visible = true, enabled = true),
        )

        val reachable = navigableRows(rows) { it.visible && it.enabled }

        assertEquals(listOf("english", "off"), reachable.map { it.id })
    }

    // Without this, arrowing down a subtitle list also seeks and changes the
    // volume, because the player's own bindings never stood down.
    @Test
    fun anOpenMenuStandsThePlayersKeysDown() {
        assertTrue(menuSwallowsKeys(menuOpen = true))
        assertFalse(menuSwallowsKeys(menuOpen = false))
    }

    // closeAllMenus clears all three together. A port that forgot one leaves a
    // trigger reading as expanded over a menu that is gone.
    @Test
    fun closingClearsEveryPieceAtOnce() {
        val open = MenuOpenState(open = true, subMenu = "subtitles", expandedTrigger = "subs")

        val closed: MenuOpenState = open.closed()

        assertFalse(closed.open)
        assertNull(closed.subMenu)
        assertNull(closed.expandedTrigger)
    }

    @Test
    fun openingTheMainMenuClosesAnyOpenSubMenu() {
        val inSubMenu = MenuOpenState(open = true, subMenu = "quality", expandedTrigger = "quality")

        val main: MenuOpenState = inSubMenu.openedMain(trigger = "settings")

        assertTrue(main.open)
        assertNull(main.subMenu)
        assertEquals("settings", main.expandedTrigger)
    }

    @Test
    fun openingASubMenuNamesItAndStaysOpen() {
        val state: MenuOpenState = MenuOpenState().openedSub("audio", trigger = "audio")

        assertTrue(state.open)
        assertEquals("audio", state.subMenu)
    }
}
