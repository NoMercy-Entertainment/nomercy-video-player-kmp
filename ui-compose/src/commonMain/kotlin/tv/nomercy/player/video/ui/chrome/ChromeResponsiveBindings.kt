// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.unit.Dp

// What the Compose bar hands the shared rule.
//
// The rule itself moved out of this module so the SwiftUI bar can call it too;
// what stays here is everything that names a Compose type. ChromeButtons and
// ChromeState are this module's, and Dp is the toolkit's unit — none of the
// three exists on the Apple side, and none of them is part of the decision.

/**
 * Rule 1: whether the consumer asked for this control at all.
 *
 * MUTE and VOLUME both answer to [ChromeButtons.volume] because the web's button
 * map points `mute` at the volume element and `volume` at nothing — the slider
 * is what rank 3 stands for, and a bar that has one has both.
 */
internal fun ChromeButtons.allows(control: ChromeControl): Boolean = when (control) {
    ChromeControl.PLAY -> playPause
    ChromeControl.MUTE, ChromeControl.VOLUME -> volume
    ChromeControl.FULLSCREEN -> fullscreen
    ChromeControl.SETTINGS -> settings
    ChromeControl.NEXT, ChromeControl.PREVIOUS -> previousNext
    ChromeControl.CHAPTER_PREV, ChromeControl.CHAPTER_NEXT -> chapters
    ChromeControl.SEEK_BACK -> seekBack
    ChromeControl.SEEK_FORWARD -> seekForward
    ChromeControl.THEATER -> theater
    ChromeControl.PIP -> pictureInPicture
    ChromeControl.SPEED -> speed
    ChromeControl.QUALITY -> quality
    ChromeControl.SUBTITLES -> subtitles
    ChromeControl.AUDIO -> audio
    ChromeControl.ASPECT_RATIO -> aspectRatio
    ChromeControl.PLAYLIST -> playlist
}

/**
 * Rule 2: whether the item can offer this control.
 *
 * One audio track is not a menu, an item with no chapters is not a player
 * missing a feature, and a queue of one has nothing to list. These cost no width
 * in the accumulation, which is the part that matters — a control nobody can see
 * must not push a later one off the end of the bar.
 */
internal fun ChromeState.lacksContentFor(control: ChromeControl): Boolean = when (control) {
    ChromeControl.CHAPTER_PREV, ChromeControl.CHAPTER_NEXT -> chapters.isEmpty()
    ChromeControl.AUDIO -> audioTracks.size <= 1
    ChromeControl.QUALITY -> qualityLevels.isEmpty()
    ChromeControl.PLAYLIST -> queueSize <= 1
    else -> false
}

internal fun boundedWidthDp(width: Dp): Int {
    val value: Float = width.value

    return if (value.isFinite()) value.toInt() else UNBOUNDED_WIDTH_DP
}

// Past every ceiling in CHROME_BREAKPOINTS, so it selects the last band the same
// way any wide screen does. Not Int.MAX_VALUE: a number that large invites an
// overflow the first time somebody adds an offset to it.
private const val UNBOUNDED_WIDTH_DP = 100_000
