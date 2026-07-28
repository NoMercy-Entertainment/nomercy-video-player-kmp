// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.video.tv.Cancellable
import tv.nomercy.player.video.tv.Scheduler
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// When the controls are on screen, and when they are not.
//
// Five rules. Four are here and the fifth belongs one layer up, because a tap on
// a screen with no pointer has to be decided against what was showing when the
// finger landed rather than when it lifted.
class ChromeControllerTest {

    private class ManualScheduler : Scheduler {
        private val pending: MutableList<Pair<Long, () -> Unit>> = mutableListOf()

        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
            val entry: Pair<Long, () -> Unit> = delayMs to action
            pending += entry
            return Cancellable { pending.remove(entry) }
        }

        fun elapse(ms: Long) {
            val due: List<Pair<Long, () -> Unit>> = pending.filter { it.first <= ms }
            pending.removeAll(due)
            due.forEach { it.second() }
        }

        val outstanding: Int get() = pending.size
    }

    private val scheduler = ManualScheduler()
    private var playing: Boolean = true

    private fun controller(playing: Boolean = true): ChromeController {
        this.playing = playing
        return ChromeController({ this.playing }, scheduler, INACTIVITY)
    }

    @Test
    fun anythingTheViewerDoesBringsTheControlsBack() {
        val chrome: ChromeController = controller()

        chrome.bumpActivity()

        assertTrue(chrome.ui.value.active)
    }

    @Test
    fun sittingStillLongEnoughTakesThemAway() {
        // The picture is what somebody came for, and controls left over it are
        // the reason players hide them at all.
        val chrome: ChromeController = controller(playing = true)
        chrome.bumpActivity()

        scheduler.elapse(INACTIVITY)

        assertFalse(chrome.ui.value.active)
    }

    @Test
    fun aPointerLeavingTheWindowIsFasterThanWaiting() {
        // A stronger signal than stopping still inside it, so it does not wait
        // the timer out.
        val chrome: ChromeController = controller(playing = true)
        chrome.bumpActivity()

        chrome.onPointerExit()

        assertFalse(chrome.ui.value.active)
    }

    @Test
    fun aPausedFilmKeepsItsControlsForever() {
        // A still image with nothing on it cannot be told apart from a player
        // that has crashed.
        val chrome: ChromeController = controller(playing = false)
        chrome.bumpActivity()

        scheduler.elapse(INACTIVITY * 10)

        assertTrue(chrome.ui.value.active)
    }

    @Test
    fun andKeepsThemWhenThePointerLeavesToo() {
        val chrome: ChromeController = controller(playing = false)
        chrome.bumpActivity()

        chrome.onPointerExit()

        assertTrue(chrome.ui.value.active)
    }

    @Test
    fun pausingBringsThemBackAndHoldsThem() {
        val chrome: ChromeController = controller(playing = true)
        chrome.bumpActivity()
        scheduler.elapse(INACTIVITY)

        playing = false
        chrome.setPlaying(false)

        assertTrue(chrome.ui.value.active)
        assertEquals0(scheduler.outstanding)
    }

    @Test
    fun anOpenMenuHoldsThemOpen() {
        // Hiding out from under a menu somebody is reading takes away the thing
        // they are using.
        val chrome: ChromeController = controller(playing = true)
        chrome.bumpActivity()

        chrome.setMenuOpen(true)
        scheduler.elapse(INACTIVITY * 5)

        assertTrue(chrome.ui.value.active)
    }

    @Test
    fun soDoesAScrubInProgress() {
        val chrome: ChromeController = controller(playing = true)
        chrome.bumpActivity()

        chrome.setScrubbing(true)
        scheduler.elapse(INACTIVITY * 5)

        assertTrue(chrome.ui.value.active)
    }

    @Test
    fun soDoesAPointerRestingOnTheControls() {
        val chrome: ChromeController = controller(playing = true)
        chrome.bumpActivity()

        chrome.setControlsHovered(true)
        scheduler.elapse(INACTIVITY * 5)

        assertTrue(chrome.ui.value.active)
    }

    @Test
    fun closingAMenuStartsTheClockRatherThanHidingAtOnce() {
        // Hiding the instant a menu closes takes the controls away from somebody
        // who has just come back to them.
        val chrome: ChromeController = controller(playing = true)
        chrome.setMenuOpen(true)

        chrome.setMenuOpen(false)

        assertTrue(chrome.ui.value.active)
        scheduler.elapse(INACTIVITY)
        assertFalse(chrome.ui.value.active)
    }

    @Test
    fun aMenuClosingWhileAScrubIsRunningChangesNothing() {
        // Independent setters is exactly how a menu closing hides controls a
        // scrub is still using. They are reasons for one answer, not separate
        // answers.
        val chrome: ChromeController = controller(playing = true)
        chrome.setScrubbing(true)
        chrome.setMenuOpen(true)

        chrome.setMenuOpen(false)
        scheduler.elapse(INACTIVITY * 5)

        assertTrue(chrome.ui.value.active)
    }

    @Test
    fun aPointerLeavingWhileAMenuIsOpenDoesNotHideIt() {
        // The pointer left because it went to the menu.
        val chrome: ChromeController = controller(playing = true)
        chrome.bumpActivity()
        chrome.setMenuOpen(true)

        chrome.onPointerExit()

        assertTrue(chrome.ui.value.active)
    }

    @Test
    fun nothingIsLeftRunningOnceTheChromeGoesAway() {
        // A timer outliving the screen is a callback into a controller nobody is
        // watching, once per screen ever opened.
        val chrome: ChromeController = controller(playing = true)
        chrome.bumpActivity()

        chrome.dispose()

        assertEquals0(scheduler.outstanding)
    }

    private fun assertEquals0(value: Int) {
        assertTrue(value == 0, "expected nothing scheduled, found $value")
    }
}

private const val INACTIVITY = 4_000L
