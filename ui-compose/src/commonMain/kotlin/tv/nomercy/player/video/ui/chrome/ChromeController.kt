// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.nomercy.player.video.ui.tv.Cancellable
import tv.nomercy.player.video.ui.tv.Scheduler

// Whether the controls are on screen.
//
// One flag, not several. The implementation this comes from had a single class
// on the container and everything read it, and the reason that held up is that
// "showing" is one question: a menu open and a scrub in progress and a pointer
// resting on a button are all reasons for the same answer, not separate answers.
public data class ChromeUi(
    val active: Boolean = false,
    val scrubbing: Boolean = false,
    val menuOpen: Boolean = false,
    val controlsHovered: Boolean = false,
)

// When the controls show and when they go away.
//
// Five rules, and they are the whole of it. Four of them are about playback and
// the fifth is about a screen with no pointer, which is why it lives one layer
// up: a tap has to be decided against what was on screen when the finger landed,
// not when it lifted.
//
// The rules are stated as one reconcile rather than as four independent setters,
// because independent setters is how a menu closing hides controls a scrub is
// still using.
// The count is the rule set. Five rules, the three things that hold the chrome
// open, and the tap pair that needs a press and a release; collapsing them into
// fewer entry points to satisfy a counter would put the whole machine behind one
// method taking a verb, which is the shape it had in the client this replaces.
@Suppress("TooManyFunctions")
public class ChromeController(
    private val isPlaying: () -> Boolean,
    private val scheduler: Scheduler,
    private val inactivityMs: Long = DEFAULT_INACTIVITY_MS,
) {

    private val state = MutableStateFlow(ChromeUi())

    public val ui: StateFlow<ChromeUi> = state.asStateFlow()

    private var hideTimer: Cancellable? = null

    // What was on screen when a finger landed, captured before anything woke.
    //
    // Rule five, and the only one that needs memory. On a touch screen a tap has
    // to be decided against what the viewer was looking at when they touched,
    // not when they lifted: the surface wakes the controls on the way down, so
    // by the time the tap resolves they are always visible and a naive toggle
    // hides them again. That is the show-then-hide flicker.
    private var tapWasActive: Boolean? = null

    // Rule one: anything the viewer does brings them back.
    public fun bumpActivity() {
        state.value = state.value.copy(active = true)
        restartTimer()
    }

    // Rule two, on a timer rather than a frame loop: idle for long enough and
    // the picture is what somebody came for.
    public fun maybeHide() {
        if (heldOpen()) return

        state.value = state.value.copy(active = false)
        cancelTimer()
    }

    // Rule three. A pointer leaving the window is a stronger signal than
    // stopping still inside it, so it does not wait out the timer.
    public fun onPointerExit() {
        if (heldOpen()) return

        state.value = state.value.copy(active = false)
        cancelTimer()
    }

    // Rule four: paused means the controls stay, forever, including when the
    // pointer leaves. A paused film with nothing on it is a still image, and
    // there is no way to tell it apart from a player that has crashed.
    public fun setPlaying(playing: Boolean) {
        if (playing) restartTimer() else showAndHold()
    }

    // The three reasons the controls are held open. Each is a state somebody is
    // in the middle of, and hiding out from under any of them takes away the
    // thing they are using.
    public fun setMenuOpen(open: Boolean) {
        state.value = state.value.copy(menuOpen = open)
        reconcile()
    }

    public fun setScrubbing(scrubbing: Boolean) {
        state.value = state.value.copy(scrubbing = scrubbing)
        reconcile()
    }

    public fun setControlsHovered(hovered: Boolean) {
        state.value = state.value.copy(controlsHovered = hovered)
        reconcile()
    }

    // Called on pointer-down, before the wake.
    public fun onTapDown() {
        tapWasActive = state.value.active
    }

    // Answers whether the tap was ours. Read once and cleared: a second tap with
    // no press before it falls back to what is actually on screen rather than to
    // a snapshot from a gesture that has finished.
    public fun onSingleTap(): Boolean {
        val wasActive: Boolean = tapWasActive ?: state.value.active
        tapWasActive = null

        // Hidden when the finger landed means the wake has already shown them,
        // and that was the whole point of the tap. Hiding now would undo it in
        // the same gesture.
        if (!wasActive) return false

        maybeHide()
        return !state.value.active
    }

    public fun dispose() {
        cancelTimer()
    }

    // Held open by anything in progress, or by being paused. Asked in one place
    // so the four callers cannot each answer it slightly differently.
    private fun heldOpen(): Boolean {
        val current: ChromeUi = state.value

        return !isPlaying() || current.menuOpen || current.scrubbing || current.controlsHovered
    }

    private fun reconcile() {
        if (heldOpen()) showAndHold() else restartTimer()
    }

    private fun showAndHold() {
        cancelTimer()
        state.value = state.value.copy(active = true)
    }

    private fun restartTimer() {
        cancelTimer()
        if (heldOpen()) return

        hideTimer = scheduler.schedule(inactivityMs) { maybeHide() }
    }

    private fun cancelTimer() {
        hideTimer?.cancel()
        hideTimer = null
    }
}

// Four seconds, matching the shipped chrome. Long enough to move a pointer from
// one control to another without them vanishing in between.
private const val DEFAULT_INACTIVITY_MS = 4_000L
