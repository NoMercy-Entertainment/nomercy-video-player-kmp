// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.video.ui.tv.Cancellable
import tv.nomercy.player.video.ui.tv.Scheduler
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The fifth rule: a tap on a screen with no pointer.
//
// It needs the full ordering rather than one synthetic tap, because the bug it
// exists to stop only appears in the ordering. The surface wakes the controls on
// the way down, so by the time the tap resolves they are always visible, and a
// toggle that read the live state would hide them again inside the same gesture.
class ChromeControllerTapRaceTest {

    private val scheduler = Scheduler { _, _ -> Cancellable { } }

    private fun controller(): ChromeController = ChromeController({ true }, scheduler, INACTIVITY)

    @Test
    fun aTapOnAHiddenChromeShowsItAndLeavesItShowing() {
        // The flicker, in the order it actually happens.
        val chrome: ChromeController = controller()

        chrome.onTapDown()
        chrome.bumpActivity()

        assertTrue(chrome.ui.value.active)
        assertFalse(chrome.onSingleTap(), "the tap that revealed them also claimed to hide them")
        assertTrue(chrome.ui.value.active, "shown then hidden in one gesture")
    }

    @Test
    fun aTapOnVisibleChromeHidesIt() {
        val chrome: ChromeController = controller()
        chrome.bumpActivity()

        chrome.onTapDown()

        assertTrue(chrome.onSingleTap())
        assertFalse(chrome.ui.value.active)
    }

    @Test
    fun theSnapshotIsUsedOnceAndThenForgotten() {
        // A stale snapshot is worse than none: it decides a later gesture from
        // what the screen looked like during an earlier one.
        // Starting hidden so the snapshot and the live state disagree. With
        // them equal nothing can tell a cleared snapshot from a kept one, which
        // is how this test passed with the clearing removed.
        val chrome: ChromeController = controller()
        chrome.onTapDown()
        chrome.bumpActivity()
        chrome.onSingleTap()

        assertTrue(chrome.ui.value.active)
        assertTrue(chrome.onSingleTap(), "a stale snapshot decided a later gesture")
        assertFalse(chrome.ui.value.active)
    }

    @Test
    fun aTapWithNoPressBeforeItFallsBackToWhatIsOnScreen() {
        // Some surfaces deliver a tap without a separate down. Reading null as
        // "was hidden" would make every one of those a no-op.
        val chrome: ChromeController = controller()
        chrome.bumpActivity()

        assertTrue(chrome.onSingleTap())
    }

    @Test
    fun aTapCannotHideChromeThatSomethingIsHoldingOpen() {
        // A menu is open. Hiding out from under it is the same failure the
        // pointer rules avoid, arriving through a finger instead.
        val chrome: ChromeController = controller()
        chrome.bumpActivity()
        chrome.setMenuOpen(true)

        chrome.onTapDown()

        assertFalse(chrome.onSingleTap())
        assertTrue(chrome.ui.value.active)
    }
}

private const val INACTIVITY = 4_000L
