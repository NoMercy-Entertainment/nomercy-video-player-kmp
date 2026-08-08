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
import tv.nomercy.player.video.tv.Cancellable
import tv.nomercy.player.video.tv.Scheduler

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
    // How many external overlays are pinning the chrome open. A count rather
    // than a flag because two of them can be open at once — a cast panel and a
    // device picker — and the first one to close must not drop the bar out from
    // under the second.
    val chromeHolds: Int = 0,
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
        // Held is a REASON TO WAIT, not a reason to stop waiting.
        //
        // This used to return and leave nothing pending, which is how the
        // chrome came to stay up forever: `isPlaying` is read rather than
        // subscribed to, so a moment where the engine answers "not playing"
        // killed the timer, and when it answered "playing" again there was no
        // event to arm a new one. Measured on the phone — the timer was armed
        // on the wake and cancelled four tenths of a second later, and nothing
        // followed it — and the viewer then had to tap somewhere to get the
        // controls to go away.
        if (heldOpen()) {
            restartTimer()
            return
        }

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
    // Pin the chrome while an overlay this controller does not own is showing.
    //
    // Its own menus do not need it — they pin through setMenuOpen — but a panel
    // anchored to the bars and built somewhere else has no way to say "do not
    // hide underneath me", and without one it does exactly that.
    //
    // Balance each hold with exactly one release.
    public fun holdChrome() {
        state.value = state.value.copy(chromeHolds = state.value.chromeHolds + 1)
        bumpActivity()
    }

    // Floored at zero rather than allowed to go negative, so a double release
    // cannot wedge the chrome permanently hidden — the reference makes the same
    // call and for the same reason.
    public fun releaseChrome() {
        val remaining: Int = maxOf(0, state.value.chromeHolds - 1)
        state.value = state.value.copy(chromeHolds = remaining)
        if (remaining == 0) bumpActivity()
    }

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

        return !isPlaying() ||
            current.menuOpen ||
            current.scrubbing ||
            current.controlsHovered ||
            current.chromeHolds > 0
    }

    private fun reconcile() {
        if (heldOpen()) showAndHold() else restartTimer()
    }

    // Shown, and checked again later. The check costs one wake-up per interval
    // while something is holding — a paused film, an open menu — and it is what
    // notices when the hold passes.
    private fun showAndHold() {
        state.value = state.value.copy(active = true)
        armTimer()
    }

    private fun restartTimer() {
        armTimer()
    }

    // Always one pending check, never none. Whether it hides is maybeHide's
    // decision, taken with the state as it is WHEN THE CHECK RUNS rather than
    // as it was when the timer was armed.
    private fun armTimer() {
        cancelTimer()
        hideTimer = scheduler.schedule(inactivityMs) { maybeHide() }
    }

    private fun cancelTimer() {
        hideTimer?.cancel()
        hideTimer = null
    }
}

// Four seconds, matching the shipped chrome. Long enough to move a pointer from
// one control to another without them vanishing in between.
// The web's own default, and public because VideoChrome now takes it as a
// parameter: a host that wants his three seconds should be able to say so
// against a named four rather than against a magic number.
public const val DEFAULT_INACTIVITY_MS: Long = 4_000L
