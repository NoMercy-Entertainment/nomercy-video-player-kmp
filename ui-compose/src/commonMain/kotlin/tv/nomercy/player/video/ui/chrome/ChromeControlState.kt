// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

// What each control looks like right now, beyond being on the bar.
//
// Three states, and they are independent. Whether a control is DRAWN is
// ChromeResponsive; this is what it looks like once it is.
//
// From desktop-ui/helpers/buttonState.ts and
// mixins/transportStateMethods.ts refreshTransportEnablement.

/**
 * Whether a control is showing a non-default state.
 *
 * One rule across eight controls, which is the thing worth porting: the web
 * adds `is-active` whenever a control is set to something other than its
 * default, and CSS turns that into the FILLED variant of the icon. Speed at
 * 1.5×, a manually pinned quality, subtitles on, picture-in-picture engaged, a
 * non-default audio track, an aspect other than uniform.
 *
 * Without it a viewer cannot tell a player at 2× from one at normal speed
 * without opening the menu to look, which is the whole reason the state is
 * shown on the button.
 */
public fun isControlActive(control: ChromeControl, state: ChromeControlValues): Boolean =
    when (control) {
        ChromeControl.SPEED -> state.rate != DEFAULT_RATE
        ChromeControl.QUALITY -> state.manualQuality
        ChromeControl.SUBTITLES -> state.subtitleTrack != null
        ChromeControl.PIP -> state.pictureInPicture
        ChromeControl.AUDIO -> state.nonDefaultAudio
        ChromeControl.ASPECT_RATIO -> state.aspect != DEFAULT_ASPECT
        ChromeControl.MUTE -> state.muted
        else -> false
    }

/**
 * Whether a control can be pressed.
 *
 * The web disables rather than hides, so the bar does not reflow every time the
 * queue reaches its last item — a control that vanished and came back would
 * move everything beside it under the viewer's finger.
 *
 * Six rules, and three of them have an edge nobody would guess:
 *
 * - Previous and next are disabled at the ends of the queue, AND on a
 *   single-item queue both are disabled rather than neither.
 * - Seek-back is disabled at exactly zero, not near it.
 * - Seek-forward is disabled within a quarter second of the end, not at it —
 *   the last frames of a decode never report the duration exactly, so an
 *   equality check leaves the button live on a finished item.
 * - The chapter jumps use a one-second grace either side, which is the same
 *   constant chapter navigation itself uses.
 */
public fun isControlEnabled(control: ChromeControl, state: ChromeControlValues): Boolean =
    when (control) {
        ChromeControl.PREVIOUS -> !(state.index <= 0 || state.queueLength <= 1)
        ChromeControl.NEXT -> !(state.index >= state.queueLength - 1 || state.queueLength <= 1)
        ChromeControl.SEEK_BACK -> state.positionSeconds > 0.0
        ChromeControl.SEEK_FORWARD -> !endOfItem(state)
        ChromeControl.CHAPTER_PREV ->
            state.chapterStarts.any { it < state.positionSeconds - CHAPTER_GRACE_SECONDS }
        ChromeControl.CHAPTER_NEXT ->
            state.chapterStarts.any { it > state.positionSeconds + CHAPTER_GRACE_SECONDS }
        else -> true
    }

/**
 * Whether theater mode is on the bar at all.
 *
 * Not a width rule and not a content rule: theater is meaningless inside
 * fullscreen and inside picture-in-picture, so the web hides it there. A
 * theater button in fullscreen is a control with nothing to do.
 */
public fun showsTheater(state: ChromeControlValues): Boolean =
    !state.fullscreen && !state.pictureInPicture

/**
 * Which volume glyph to draw.
 *
 * Four steps rather than two, and the thresholds are the web's: muted at zero
 * or when muted, low under 30, medium up to and including 60, high above. A
 * two-state icon would make the button stop describing the level it is set to.
 */
public fun volumeLevel(state: ChromeControlValues): VolumeLevel = when {
    state.muted || state.volumePercent == 0 -> VolumeLevel.Muted
    state.volumePercent < VOLUME_LOW_MAX -> VolumeLevel.Low
    state.volumePercent <= VOLUME_MEDIUM_MAX -> VolumeLevel.Medium
    else -> VolumeLevel.High
}

public enum class VolumeLevel { Muted, Low, Medium, High }

/** Everything the two rules above read, in one value. */
public data class ChromeControlValues(
    val index: Int = 0,
    val queueLength: Int = 1,
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val chapterStarts: List<Double> = emptyList(),
    val rate: Double = DEFAULT_RATE,
    val manualQuality: Boolean = false,
    val subtitleTrack: Int? = null,
    val nonDefaultAudio: Boolean = false,
    val aspect: String = DEFAULT_ASPECT,
    val pictureInPicture: Boolean = false,
    val fullscreen: Boolean = false,
    val muted: Boolean = false,
    val volumePercent: Int = 100,
)

// A duration of zero means it is not known yet, and a player that has not
// reported one has not ended.
private fun endOfItem(state: ChromeControlValues): Boolean =
    state.durationSeconds > 0.0 &&
        state.positionSeconds >= state.durationSeconds - END_OF_ITEM_TOLERANCE

/** The web's `rate !== 1`. */
public const val DEFAULT_RATE: Double = 1.0

/** The web's `'uniform'`. */
public const val DEFAULT_ASPECT: String = "uniform"

/**
 * The web's `dur - 0.25`. The last frames of a decode never report the duration
 * exactly, so an equality check leaves seek-forward live on a finished item.
 */
public const val END_OF_ITEM_TOLERANCE: Double = 0.25

/** The same one second the chapter jumps themselves use. */
public const val CHAPTER_GRACE_SECONDS: Double = 1.0

private const val VOLUME_LOW_MAX = 30
private const val VOLUME_MEDIUM_MAX = 60
