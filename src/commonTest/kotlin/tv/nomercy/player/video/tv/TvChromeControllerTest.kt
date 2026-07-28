// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

import tv.nomercy.player.core.input.PlayerKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// What a remote does to the chrome.
//
// None of this could be tested where it came from: it lived inside a composable
// and reached a playback store, a navigation controller and the system audio
// service from there. The behaviour is unchanged and it is now driveable, which
// is the whole point of pulling it out.
class TvChromeControllerTest {

    private class Callbacks : TvChromeCallbacks {
        val calls: MutableList<String> = mutableListOf()
        val seeks: MutableList<Float> = mutableListOf()
        val overrides: MutableList<Float?> = mutableListOf()

        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun togglePlay() { calls += "togglePlay" }
        override fun seek(seconds: Float) { seeks += seconds }
        override fun overrideTime(seconds: Float?) { overrides += seconds }
        override fun restart() { calls += "restart" }
        override fun next() { calls += "next" }
        override fun exitPlayer() { calls += "exitPlayer" }
    }

    // Runs what it was given only when the test says so, which is what makes the
    // auto-hide assertable without waiting five seconds for it.
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

    private val callbacks = Callbacks()
    private val scheduler = ManualScheduler()

    private fun watching(playing: Boolean = true): TvChromeController {
        val controller = TvChromeController(callbacks, scheduler, playing, startOnPreScreen = true)
        controller.dismissPreScreen()
        return controller
    }

    @Test
    fun asidewaysPressStartsScrubbingAndStopsTheFilm() {
        // Letting it run while somebody hunts for a moment turns a careful search
        // into a moving target.
        val controller: TvChromeController = watching()

        assertTrue(controller.onKey(PlayerKey.Left))

        assertTrue(controller.ui.value.seekMode)
        assertTrue(controller.ui.value.controlsVisible)
        assertEquals(listOf("pause"), callbacks.calls)
    }

    @Test
    fun whileScrubbingTheArrowsBelongToTheBar() {
        // The bar has focus and is moving the preview. The chrome reading them as
        // well would move it twice per press.
        val controller: TvChromeController = watching()
        controller.onKey(PlayerKey.Left)

        assertFalse(controller.onKey(PlayerKey.Left))
        assertFalse(controller.onKey(PlayerKey.Right))
    }

    @Test
    fun aVerticalPressRevealsTheControls() {
        val controller: TvChromeController = watching()

        assertTrue(controller.onKey(PlayerKey.Up))

        assertTrue(controller.ui.value.controlsVisible)
    }

    @Test
    fun withTheControlsAlreadyUpTheKeyGoesToWhateverIsFocused() {
        // And the timer restarts, so they do not vanish under somebody who is
        // in the middle of using them.
        val controller: TvChromeController = watching()
        controller.onKey(PlayerKey.Up)

        assertFalse(controller.onKey(PlayerKey.Down))
        assertTrue(controller.ui.value.controlsVisible)
    }

    @Test
    fun theCentreButtonPlaysAndPausesOnlyWhileNothingIsOnScreen() {
        val controller: TvChromeController = watching()

        assertTrue(controller.onKey(PlayerKey.Center))
        assertEquals(listOf("togglePlay"), callbacks.calls)
    }

    @Test
    fun withControlsUpTheCentreButtonBelongsToTheFocusedControl() {
        // Otherwise pressing the play button both presses it and toggles
        // playback again behind it, which reads as the button not working.
        val controller: TvChromeController = watching()
        controller.onKey(PlayerKey.Up)
        callbacks.calls.clear()

        assertFalse(controller.onKey(PlayerKey.Center))
        assertEquals(emptyList(), callbacks.calls)
    }

    @Test
    fun aDialogOwnsEveryKeyWhileItIsOpen() {
        // The implementation this replaces checked one dialog and forgot the
        // others, so a press meant for a list moved the film behind it.
        val controller: TvChromeController = watching()
        controller.openDialog(TvDialog.Language)

        assertFalse(controller.onKey(PlayerKey.Left))
        assertFalse(controller.onKey(PlayerKey.Center))
        assertEquals(emptyList(), callbacks.seeks)
    }

    @Test
    fun backUndoesTheMostRecentThingRatherThanLeaving() {
        // Layered, and the order is the behaviour. It is what makes a remote
        // with one back button usable at all.
        val controller: TvChromeController = watching()
        controller.openDialog(TvDialog.Language)

        assertTrue(controller.onBack())

        assertEquals(TvDialog.None, controller.ui.value.dialog)
        assertTrue(controller.ui.value.preScreenVisible)
    }

    @Test
    fun aSearchGoesBackToTheListItWasOpenedFrom() {
        // Not all the way out. A viewer who dismissed one thing too many has
        // lost their place for no reason.
        val controller: TvChromeController = watching()
        controller.openDialog(TvDialog.SubtitleSearch)

        assertTrue(controller.onBack())

        assertEquals(TvDialog.Subtitle, controller.ui.value.dialog)
    }

    @Test
    fun backFromTheControlsJustHidesThem() {
        val controller: TvChromeController = watching()
        controller.onKey(PlayerKey.Up)

        assertTrue(controller.onBack())

        assertFalse(controller.ui.value.controlsVisible)
        assertFalse(controller.ui.value.preScreenVisible)
    }

    @Test
    fun backFromBarePlaybackPausesAndShowsThePreScreen() {
        // Somebody pressing back has stopped watching. A film that carries on
        // behind a menu is one they have to rewind afterwards.
        val controller: TvChromeController = watching()

        assertTrue(controller.onBack())

        assertTrue(controller.ui.value.preScreenVisible)
        assertEquals(listOf("pause"), callbacks.calls)
    }

    @Test
    fun backFromThePreScreenLeavesThePlayer() {
        // The bottom of the stack, and the only place leaving is right.
        val controller = TvChromeController(callbacks, scheduler, playing = true, startOnPreScreen = true)

        assertTrue(controller.onBack())

        assertEquals(listOf("exitPlayer"), callbacks.calls)
    }

    @Test
    fun theControlsGoAwayOnTheirOwnWhileTheFilmIsPlaying() {
        val controller: TvChromeController = watching(playing = true)
        controller.onKey(PlayerKey.Up)

        scheduler.elapse(5_000)

        assertFalse(controller.ui.value.controlsVisible)
    }

    @Test
    fun theyStayWhileTheTopBarIsBeingRead() {
        // Focus is on it, so somebody is looking at it. Timing out under them
        // is the chrome deciding they were finished.
        val controller: TvChromeController = watching(playing = true)
        controller.onKey(PlayerKey.Up)
        controller.setTopBarFocus(true)

        scheduler.elapse(5_000)

        assertTrue(controller.ui.value.controlsVisible)
    }

    @Test
    fun theyStayWhileSomebodyIsScrubbing() {
        val controller: TvChromeController = watching(playing = true)
        controller.onKey(PlayerKey.Left)

        scheduler.elapse(5_000)

        assertTrue(controller.ui.value.controlsVisible)
    }

    @Test
    fun aPausedFilmKeepsItsControls() {
        // A still frame with no controls on it is a television that looks
        // frozen, and there is nothing else on screen to say otherwise.
        val controller: TvChromeController = watching(playing = true)
        controller.onKey(PlayerKey.Up)

        controller.onPlaybackChanged(isPlaying = false)
        scheduler.elapse(10_000)

        assertTrue(controller.ui.value.controlsVisible)
    }

    @Test
    fun finishingAScrubOnAPausedFilmDoesNotStartTheHideTimer() {
        // The scrub paused it on the way in, so committing lands on a paused
        // picture. Timing the controls out from there leaves a still frame with
        // nothing on it, and no way to tell the player is alive.
        val controller: TvChromeController = watching(playing = true)
        controller.onKey(PlayerKey.Left)
        controller.onPlaybackChanged(isPlaying = false)

        controller.commitSeek(120f)
        scheduler.elapse(10_000)

        assertTrue(controller.ui.value.controlsVisible)
    }

    @Test
    fun lettingGoOfTheTopBarOnAPausedFilmDoesNotStartItEither() {
        val controller: TvChromeController = watching(playing = true)
        controller.onKey(PlayerKey.Up)
        controller.onPlaybackChanged(isPlaying = false)
        controller.setTopBarFocus(true)

        controller.setTopBarFocus(false)
        scheduler.elapse(10_000)

        assertTrue(controller.ui.value.controlsVisible)
    }

    @Test
    fun nothingIsLeftScheduledOnceThePlayerGoesAway() {
        // A timer outliving the screen is a callback into a controller nobody is
        // watching, once per screen ever opened.
        val controller: TvChromeController = watching(playing = true)
        controller.onKey(PlayerKey.Up)

        controller.dispose()

        assertEquals(0, scheduler.outstanding)
    }

    @Test
    fun committingAScrubPutsTheDisplayBackOnTheRealPosition() {
        // Without it the bar keeps showing a position somebody scrolled to and
        // never played.
        val controller: TvChromeController = watching()
        controller.onKey(PlayerKey.Left)

        controller.commitSeek(120f)

        assertEquals(listOf(120f), callbacks.seeks)
        assertEquals(listOf<Float?>(null), callbacks.overrides)
        assertFalse(controller.ui.value.seekMode)
    }

    @Test
    fun abandoningAScrubMovesNothing() {
        val controller: TvChromeController = watching()
        controller.onKey(PlayerKey.Left)

        assertTrue(controller.onBack())

        assertEquals(emptyList(), callbacks.seeks)
        assertFalse(controller.ui.value.seekMode)
    }

    @Test
    fun theTopBarKeepsTheArrowsWhileItHasFocus() {
        // They are moving between its buttons. Turning them into a scrub would
        // take the film away from under somebody who was reading the title.
        val controller: TvChromeController = watching()
        controller.onKey(PlayerKey.Up)
        controller.setTopBarFocus(true)

        assertFalse(controller.onKey(PlayerKey.Left))
        assertFalse(controller.ui.value.seekMode)
    }

    @Test
    fun thePreScreenSwallowsTheDirectionalKeysItDoesNotOwn() {
        // Its own buttons are focusable and handle their own presses. The chrome
        // reacting as well would scrub a film the viewer has not resumed.
        val controller = TvChromeController(callbacks, scheduler, playing = false, startOnPreScreen = true)

        assertFalse(controller.onKey(PlayerKey.Left))
        assertFalse(controller.onKey(PlayerKey.Up))
        assertFalse(controller.ui.value.seekMode)
    }

    @Test
    fun theTransportKeysEveryRemoteHasActuallyDoSomething() {
        // Only the D-pad was handled, so the button with the triangle printed on
        // it did nothing on every television this has ever run on.
        val controller: TvChromeController = watching()

        assertTrue(controller.onKey(PlayerKey.PlayPause))

        assertEquals(listOf("togglePlay"), callbacks.calls)
        assertTrue(controller.ui.value.controlsVisible, "and the bar comes back, as pressing pause implies")
    }

    @Test
    fun playMeansPlayEvenWhenTheFilmAlreadyIs() {
        // A headset sending MediaPlay on reconnect means play, not toggle. Read
        // as a toggle it pauses the film the moment the headphones come back.
        val controller: TvChromeController = watching(playing = true)

        assertTrue(controller.onKey(PlayerKey.MediaPlay))

        assertEquals(listOf("play"), callbacks.calls)
    }
}
