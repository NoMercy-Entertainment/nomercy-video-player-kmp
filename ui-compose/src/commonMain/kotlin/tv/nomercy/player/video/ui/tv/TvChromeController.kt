// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.nomercy.player.core.input.PlayerKey

// What a remote does to the chrome, as a machine rather than as a screen.
//
// This is the extraction. The implementation it comes from lives inside a
// composable and reaches a playback store, a navigation controller and the
// system audio service from there, so none of it could be tested and none of it
// could be moved to a platform without those three. The behaviour is the same;
// what changed is that it can now be driven.
//
// It is also what the SwiftUI chrome will drive, which is the point: a
// television and an Apple TV disagree about how a view is built and agree
// completely about what a back press should do.
//
// The method count is the transition table. Collapsing the branches into fewer
// functions to satisfy a counter would put the whole state machine in one body,
// which is the shape it had before this extraction and the reason it could not
// be read.
@Suppress("TooManyFunctions")
public class TvChromeController(
    public val callbacks: TvChromeCallbacks,
    private val scheduler: Scheduler,
    private var playing: Boolean = false,
    startOnPreScreen: Boolean = true,
    private val content: TvContentCallbacks? = null,
) {

    private val state = MutableStateFlow(TvChromeUi(preScreenVisible = startOnPreScreen))

    public val ui: StateFlow<TvChromeUi> = state.asStateFlow()

    private var autoHide: Cancellable? = null

    // Answers whether the press was consumed. False hands it back, which is what
    // lets a focused button take its own centre press and what lets the host
    // decide that a back press at the bottom means leaving.
    public fun onKey(key: PlayerKey): Boolean = when {
        // A dialog owns every key while it is open. The one this replaces
        // checked one dialog and forgot the others, so a press meant for a list
        // moved the film behind it.
        state.value.dialog != TvDialog.None -> false

        // So does the scrub bar. While it has focus the arrows are moving the
        // preview, and the chrome interpreting them as well would move it twice.
        state.value.seekMode -> false

        else -> onKeyWhileWatching(key)
    }

    private fun onKeyWhileWatching(key: PlayerKey): Boolean = when (key) {
        PlayerKey.Left, PlayerKey.Right -> enterSeek()
        PlayerKey.Up, PlayerKey.Down -> revealControls()
        PlayerKey.Center -> toggleWhenNothingElseWants()
        PlayerKey.Back -> onBack()
        else -> false
    }

    // A sideways press with nothing focused means scrubbing, and scrubbing means
    // pausing first. Letting it run while somebody hunts for a moment turns a
    // careful search into a moving target.
    private fun enterSeek(): Boolean {
        val current: TvChromeUi = state.value
        if (current.preScreenVisible || current.topBarHasFocus) return false

        cancelAutoHide()
        if (playing) callbacks.pause()
        state.value = current.copy(controlsVisible = true, seekMode = true)
        return true
    }

    // Already-visible controls are not re-revealed; the press goes to whatever
    // is focused and the timer is restarted so it does not vanish under a
    // viewer who is using it.
    private fun revealControls(): Boolean {
        val current: TvChromeUi = state.value
        if (current.preScreenVisible) return false

        if (current.controlsVisible) {
            scheduleAutoHide()
            return false
        }

        state.value = current.copy(controlsVisible = true)
        scheduleAutoHide()
        return true
    }

    private fun toggleWhenNothingElseWants(): Boolean {
        val current: TvChromeUi = state.value
        if (current.controlsVisible || current.preScreenVisible) return false

        callbacks.togglePlay()
        return true
    }

    // Layered, and the order is the behaviour. Each press undoes the most recent
    // thing rather than jumping to the exit, which is what makes a remote with
    // one back button usable at all.
    public fun onBack(): Boolean {
        val current: TvChromeUi = state.value

        return when {
            current.dialog == TvDialog.SubtitleSearch -> closeDialogTo(TvDialog.Subtitle)
            current.dialog != TvDialog.None -> returnToPreScreen()
            current.seekMode -> cancelSeek()
            current.controlsVisible -> hideControls()
            !current.preScreenVisible -> pauseAndShowPreScreen()
            else -> leave()
        }
    }

    // A search opened from the subtitle list goes back to that list rather than
    // all the way out. Anything else is a viewer losing their place because they
    // dismissed one thing too many.
    private fun closeDialogTo(previous: TvDialog): Boolean {
        state.value = state.value.copy(dialog = previous)
        return true
    }

    private fun returnToPreScreen(): Boolean {
        state.value = state.value.copy(dialog = TvDialog.None, preScreenVisible = true)
        return true
    }

    private fun hideControls(): Boolean {
        cancelAutoHide()
        state.value = state.value.copy(controlsVisible = false)
        return true
    }

    // Pausing on the way is deliberate. A viewer pressing back has stopped
    // watching, and a film that carries on behind a menu is one they have to
    // rewind afterwards.
    private fun pauseAndShowPreScreen(): Boolean {
        cancelAutoHide()
        if (playing) callbacks.pause()
        state.value = state.value.copy(preScreenVisible = true, controlsVisible = false)
        return true
    }

    private fun leave(): Boolean {
        callbacks.exitPlayer()
        return true
    }

    // Choosing closes the list. A viewer who picked a track is done with it, and
    // leaving it open means a second press to get back to the film they changed
    // it for.
    public fun chooseEpisode(id: String) {
        content?.selectEpisode(id)
        returnToPreScreen()
    }

    public fun chooseAudioTrack(id: String) {
        content?.selectAudioTrack(id)
        returnToPreScreen()
    }

    public fun chooseSubtitleTrack(id: String) {
        content?.selectSubtitleTrack(id)
        returnToPreScreen()
    }

    public fun openDialog(dialog: TvDialog) {
        cancelAutoHide()
        state.value = state.value.copy(dialog = dialog, controlsVisible = false, preScreenVisible = false)
    }

    public fun dismissPreScreen() {
        state.value = state.value.copy(preScreenVisible = false)
    }

    public fun commitSeek(toSeconds: Float) {
        callbacks.seek(toSeconds)
        callbacks.overrideTime(null)
        state.value = state.value.copy(seekMode = false)
        scheduleAutoHide()
    }

    // Puts the display back where the film actually is. Without it the bar keeps
    // showing the position somebody scrolled to and never played.
    public fun cancelSeek(): Boolean {
        callbacks.overrideTime(null)
        state.value = state.value.copy(seekMode = false)
        scheduleAutoHide()
        return true
    }

    // The top bar holds focus while a viewer reads it, so the controls must not
    // time out underneath them and the arrows must not turn into a scrub.
    public fun setTopBarFocus(focused: Boolean) {
        state.value = state.value.copy(topBarHasFocus = focused)
        if (focused) cancelAutoHide() else scheduleAutoHide()
    }

    // Pausing shows the controls and keeps them; playing lets them go. A paused
    // film with no controls on screen is a television that looks frozen.
    public fun onPlaybackChanged(isPlaying: Boolean) {
        playing = isPlaying

        val current: TvChromeUi = state.value
        if (current.preScreenVisible || current.dialog != TvDialog.None) return

        if (isPlaying) {
            scheduleAutoHide()
        } else {
            cancelAutoHide()
            state.value = current.copy(controlsVisible = true)
        }
    }

    public fun showVolume() {
        state.value = state.value.copy(volumeIndicatorVisible = true)
        scheduler.schedule(VOLUME_VISIBLE_MS) {
            state.value = state.value.copy(volumeIndicatorVisible = false)
        }
    }

    public fun dispose() {
        cancelAutoHide()
    }

    // Only while playing. Hiding the controls over a paused picture leaves a
    // still frame and no indication the player is alive.
    private fun scheduleAutoHide() {
        cancelAutoHide()
        if (!playing) return

        // No second check inside. Taking focus on the top bar and entering the
        // scrub both cancel this, so a guard here could never be the thing that
        // saved the controls — removing it changed no test, which is what dead
        // defensiveness looks like from the outside.
        autoHide = scheduler.schedule(AUTO_HIDE_MS) {
            state.value = state.value.copy(controlsVisible = false)
        }
    }

    private fun cancelAutoHide() {
        autoHide?.cancel()
        autoHide = null
    }
}

// Five seconds, matching the client this is extracted from. Long enough to read
// the bar and reach for a button, short enough that it is gone before it starts
// covering the picture.
private const val AUTO_HIDE_MS = 5_000L

private const val VOLUME_VISIBLE_MS = 2_000L
