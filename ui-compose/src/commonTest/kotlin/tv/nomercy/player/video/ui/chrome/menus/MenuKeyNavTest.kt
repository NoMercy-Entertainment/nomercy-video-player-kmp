// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The arrow walk's arithmetic, digit for digit against `wireMenuKeyboardNav`:
 *
 *     const nextIdx = key === 'ArrowDown'
 *         ? (activeIdx + 1) % rows.length
 *         : (activeIdx - 1 + rows.length) % rows.length;
 *
 * The cases that matter are the ones a rewrite "improves" away: both wraps, and
 * the two answers for a list nothing in has focus — Down lands on the first row
 * and Up lands one SHORT of the last, because -1 - 1 is -2 and the modulo says
 * so. A clamp, or an Up-from-nothing that helpfully picks the last row, is a
 * different player from the one in the browser.
 */
class MenuKeyNavTest {

    @Test
    fun downFromNothingLandsOnTheFirstRow() {
        assertEquals(0, menuNavTarget(index = -1, count = 5, down = true))
    }

    @Test
    fun upFromNothingLandsOneShortOfTheEnd() {
        // (-1 - 1 + 5) % 5 — the web's own answer, not the last row.
        assertEquals(3, menuNavTarget(index = -1, count = 5, down = false))
    }

    @Test
    fun downFromTheLastRowWrapsToTheFirst() {
        assertEquals(0, menuNavTarget(index = 4, count = 5, down = true))
    }

    @Test
    fun upFromTheFirstRowWrapsToTheLast() {
        assertEquals(4, menuNavTarget(index = 0, count = 5, down = false))
    }

    @Test
    fun aSingleRowAlwaysAnswersItself() {
        assertEquals(0, menuNavTarget(index = 0, count = 1, down = true))
        assertEquals(0, menuNavTarget(index = 0, count = 1, down = false))
        assertEquals(0, menuNavTarget(index = -1, count = 1, down = false))
    }

    @Test
    fun ordinaryStepsMoveOneRow() {
        assertEquals(2, menuNavTarget(index = 1, count = 5, down = true))
        assertEquals(1, menuNavTarget(index = 2, count = 5, down = false))
    }
}
