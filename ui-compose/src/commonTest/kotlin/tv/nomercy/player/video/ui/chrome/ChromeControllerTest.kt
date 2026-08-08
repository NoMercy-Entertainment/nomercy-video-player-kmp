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
        // A pending check, not none. It used to be none, and that is precisely
        // how a hold that passed left the chrome up forever — see
        // aHoldThatPassesDoesNotKillTheAutohideForever. The check runs, sees the
        // player still paused, and re-arms; nothing hides while it is held.
        assertTrue(scheduler.outstanding > 0, "a paused chrome left no check pending")

        scheduler.elapse(INACTIVITY)
        assertTrue(chrome.ui.value.active, "the check hid a paused player")
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
    // An overlay this controller does not own can pin the bars.
    //
    // Its own menus pin through setMenuOpen, but a cast panel or device picker
    // anchored to the bars is built elsewhere and had no way to say "do not
    // hide underneath me" — so it hid underneath, which is the bug the
    // reference's holdChrome exists to prevent.
    @Test
    fun anExternalOverlayCanPinTheChromeOpen() {
        val chrome: ChromeController = controller(playing = true)
        chrome.bumpActivity()

        chrome.holdChrome()
        scheduler.elapse(INACTIVITY)

        assertTrue(chrome.ui.value.active)
    }

    @Test
    fun theChromeHidesAgainOnceTheLastHoldGoes() {
        val chrome: ChromeController = controller(playing = true)
        chrome.holdChrome()
        chrome.releaseChrome()

        scheduler.elapse(INACTIVITY)

        assertFalse(chrome.ui.value.active)
    }

    // Two overlays open at once: the first to close must not drop the bars out
    // from under the second, which is why this is a count and not a flag.
    @Test
    fun oneOfTwoHoldsClosingKeepsTheChromeUp() {
        val chrome: ChromeController = controller(playing = true)
        chrome.holdChrome()
        chrome.holdChrome()

        chrome.releaseChrome()
        scheduler.elapse(INACTIVITY)

        assertTrue(chrome.ui.value.active)
    }

    // A double release cannot wedge the chrome permanently hidden by driving
    // the count negative — the next hold has to work.
    @Test
    fun anExtraReleaseCannotWedgeTheChromeHidden() {
        val chrome: ChromeController = controller(playing = true)
        chrome.holdChrome()
        chrome.releaseChrome()
        chrome.releaseChrome()

        chrome.holdChrome()
        scheduler.elapse(INACTIVITY)

        assertTrue(chrome.ui.value.active)
    }


    @Test
    fun aHoldThatPassesDoesNotKillTheAutohideForever() {
        // The defect Stoney reported as "pressing a button in the bottom bar
        // prevents the overlay from hiding on inactivity, requiring a defocus
        // tap". Measured on the phone: the timer is armed on the wake and
        // CANCELLED four tenths of a second later, and nothing ever arms it
        // again —
        //
        //   [sched] schedule 4000ms active=true
        //   [sched] cancel                        <- and then nothing, ever
        //
        // The cause is that restartTimer cancels first and returns when
        // something holds the chrome open, while `isPlaying` is READ rather
        // than subscribed to: a moment where the engine answers "not playing"
        // kills the timer, and when it starts answering "playing" again there
        // is no event to re-arm it. The chrome then stays up until the viewer
        // touches something.
        val chrome: ChromeController = controller(playing = true)

        chrome.bumpActivity()
        playing = false
        chrome.bumpActivity()
        playing = true

        // No setPlaying() here ON PURPOSE. That is the whole point: nothing
        // tells the controller the hold has passed, exactly as on the device.
        scheduler.elapse(INACTIVITY)
        scheduler.elapse(INACTIVITY)

        assertFalse(chrome.ui.value.active, "the autohide never came back after a hold passed")
    }

    @Test
    fun aChromeThatIsHeldOpenStillHasAPendingCheck() {
        // The other half: while something genuinely holds the chrome — paused,
        // a menu open — there must still be a check pending, or the hold
        // passing has nothing to notice it.
        val chrome: ChromeController = controller(playing = false)

        chrome.bumpActivity()

        assertTrue(scheduler.outstanding > 0, "a held chrome left no check pending")
    }
}

private const val INACTIVITY = 4_000L
