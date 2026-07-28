// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.touch

/**
 * Where a tap lands, and what it means.
 *
 * The web's touch-zones plugin as a model, from `plugins/touch-zones/index.ts`.
 * The geometry there is a CSS grid and the geometry here is arithmetic on a
 * fraction, but the ZONES are the same and so is what each gesture does — which
 * is the part a viewer feels.
 *
 * A three-column by six-row grid:
 *
 * ```
 *        col 1          col 2            col 3
 *  row 1  seek back      volume up        seek forward     (mobile only)
 *  row 2  seek back      volume up        seek forward     (mobile only)
 *  row 3  seek back      play / pause     seek forward
 *  row 4  seek back      play / pause     seek forward
 *  row 5  seek back      play / pause     seek forward
 *  row 6  seek back      volume down      seek forward     (mobile only)
 * ```
 *
 * Without the volume zones the centre column is play/pause for all six rows.
 * That is not a simplification for small screens — it is the other way round:
 * the volume zones are ADDED on a touch device, because a mouse has a wheel and
 * a finger has nothing.
 *
 * The gesture rules are the surprising half:
 *
 * - **Left and right**: a single tap shows or hides the controls. It does NOT
 *   seek. Double-tap seeks.
 * - **Centre**: a single tap toggles playback, a double-tap toggles fullscreen.
 * - **Volume**: a single tap shows or hides the controls, a double-tap changes
 *   the volume.
 *
 * So a single tap means the same thing everywhere except the centre, and every
 * seek needs two taps. Getting that backwards — seeking on a single tap at the
 * edges — is the obvious design and it makes a player that jumps whenever
 * somebody touches it to see the controls.
 */
public enum class TouchZone {
    SEEK_BACK,
    SEEK_FORWARD,
    PLAY_PAUSE,
    VOLUME_UP,
    VOLUME_DOWN,
}

/** What a gesture in a zone asks for. */
public enum class TouchAction {
    TOGGLE_CONTROLS,
    TOGGLE_PLAYBACK,
    TOGGLE_FULLSCREEN,
    SEEK_BACK,
    SEEK_FORWARD,
    VOLUME_UP,
    VOLUME_DOWN,
}

/**
 * The zone a point falls in.
 *
 * [x] and [y] are fractions of the surface, 0 to 1, so this is the same
 * arithmetic on a phone and on a desktop window and needs no pixel density.
 *
 * [volumeZones] is the web's mobile check. Passed in rather than detected here
 * because "is this a touch device" is a platform question and this file is
 * common code.
 */
public fun zoneAt(x: Float, y: Float, volumeZones: Boolean): TouchZone {
    val column: Int = (x * COLUMNS).toInt().coerceIn(0, COLUMNS - 1)

    return when {
        column == 0 -> TouchZone.SEEK_BACK
        column == COLUMNS - 1 -> TouchZone.SEEK_FORWARD
        else -> centreZoneAt(y, volumeZones)
    }
}

// The centre column, which is one zone without volume and three with.
//
// Rows 1-2 of six and row 6 of six. The web writes these as grid lines; the
// fractions are the same boundaries.
private fun centreZoneAt(y: Float, volumeZones: Boolean): TouchZone {
    if (!volumeZones) return TouchZone.PLAY_PAUSE

    val row: Int = (y * ROWS).toInt().coerceIn(0, ROWS - 1)
    return when {
        row < VOLUME_UP_ROWS -> TouchZone.VOLUME_UP
        row >= ROWS - VOLUME_DOWN_ROWS -> TouchZone.VOLUME_DOWN
        else -> TouchZone.PLAY_PAUSE
    }
}

/**
 * What one tap in a zone does.
 *
 * Everything except the centre shows or hides the controls. A viewer touching
 * the left of the screen wants to see where they are, not to jump backwards.
 */
public fun singleTapAction(zone: TouchZone): TouchAction = when (zone) {
    TouchZone.PLAY_PAUSE -> TouchAction.TOGGLE_PLAYBACK
    else -> TouchAction.TOGGLE_CONTROLS
}

/** What two taps in a zone do. */
public fun doubleTapAction(zone: TouchZone): TouchAction = when (zone) {
    TouchZone.SEEK_BACK -> TouchAction.SEEK_BACK
    TouchZone.SEEK_FORWARD -> TouchAction.SEEK_FORWARD
    TouchZone.PLAY_PAUSE -> TouchAction.TOGGLE_FULLSCREEN
    TouchZone.VOLUME_UP -> TouchAction.VOLUME_UP
    TouchZone.VOLUME_DOWN -> TouchAction.VOLUME_DOWN
}

/**
 * How long after a tap a second one still counts as a double.
 *
 * The web's `doubleTapThreshold` default. Named rather than inlined because a
 * different number here is a player that feels different under the same finger.
 */
public const val DOUBLE_TAP_THRESHOLD_MS: Long = 300

/** The web's `seekSeconds` default, and the step its indicator counts in. */
public const val TOUCH_SEEK_SECONDS: Double = 10.0

private const val COLUMNS = 3
private const val ROWS = 6
private const val VOLUME_UP_ROWS = 2
private const val VOLUME_DOWN_ROWS = 1
