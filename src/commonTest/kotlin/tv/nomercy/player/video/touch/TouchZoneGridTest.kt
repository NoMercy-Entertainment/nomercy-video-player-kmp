// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.touch

import kotlin.test.Test
import kotlin.test.assertEquals

// The gesture rules, as cases.
//
// The one worth protecting is that a single tap at the edges shows the controls
// and does NOT seek. Seeking on a single tap is the obvious design and it makes
// a player that jumps every time somebody touches it to see where they are.
class TouchZoneGridTest {

    @Test
    fun theOuterThirdsAreTheSeekZones() {
        assertEquals(TouchZone.SEEK_BACK, zoneAt(x = 0.1f, y = 0.5f, volumeZones = false))
        assertEquals(TouchZone.SEEK_FORWARD, zoneAt(x = 0.9f, y = 0.5f, volumeZones = false))
    }

    @Test
    fun theCentreColumnIsPlayPauseWithoutVolumeZones() {
        for (y in listOf(0.05f, 0.5f, 0.95f)) {
            assertEquals(TouchZone.PLAY_PAUSE, zoneAt(x = 0.5f, y = y, volumeZones = false))
        }
    }

    // Added on a touch device rather than removed on a desktop: a mouse has a
    // wheel and a finger has nothing.
    @Test
    fun volumeZonesTakeTheTopTwoRowsAndTheBottomOne() {
        // Rows 1 and 2 of six.
        assertEquals(TouchZone.VOLUME_UP, zoneAt(x = 0.5f, y = 0.05f, volumeZones = true))
        assertEquals(TouchZone.VOLUME_UP, zoneAt(x = 0.5f, y = 0.30f, volumeZones = true))
        // Rows 3, 4 and 5.
        assertEquals(TouchZone.PLAY_PAUSE, zoneAt(x = 0.5f, y = 0.40f, volumeZones = true))
        assertEquals(TouchZone.PLAY_PAUSE, zoneAt(x = 0.5f, y = 0.70f, volumeZones = true))
        // Row 6.
        assertEquals(TouchZone.VOLUME_DOWN, zoneAt(x = 0.5f, y = 0.95f, volumeZones = true))
    }

    // The volume zones are the centre column only. A tap at the top left is a
    // seek zone, not volume.
    @Test
    fun theVolumeZonesDoNotReachTheOuterColumns() {
        assertEquals(TouchZone.SEEK_BACK, zoneAt(x = 0.1f, y = 0.05f, volumeZones = true))
        assertEquals(TouchZone.SEEK_FORWARD, zoneAt(x = 0.9f, y = 0.95f, volumeZones = true))
    }

    // The rule that matters. Every edge tap shows the controls; only the centre
    // touches playback.
    @Test
    fun aSingleTapNeverSeeks() {
        assertEquals(TouchAction.TOGGLE_CONTROLS, singleTapAction(TouchZone.SEEK_BACK))
        assertEquals(TouchAction.TOGGLE_CONTROLS, singleTapAction(TouchZone.SEEK_FORWARD))
        assertEquals(TouchAction.TOGGLE_CONTROLS, singleTapAction(TouchZone.VOLUME_UP))
        assertEquals(TouchAction.TOGGLE_CONTROLS, singleTapAction(TouchZone.VOLUME_DOWN))
        assertEquals(TouchAction.TOGGLE_PLAYBACK, singleTapAction(TouchZone.PLAY_PAUSE))
    }

    @Test
    fun aDoubleTapDoesTheZonesRealJob() {
        assertEquals(TouchAction.SEEK_BACK, doubleTapAction(TouchZone.SEEK_BACK))
        assertEquals(TouchAction.SEEK_FORWARD, doubleTapAction(TouchZone.SEEK_FORWARD))
        assertEquals(TouchAction.VOLUME_UP, doubleTapAction(TouchZone.VOLUME_UP))
        assertEquals(TouchAction.VOLUME_DOWN, doubleTapAction(TouchZone.VOLUME_DOWN))
    }

    // Two taps in the middle is fullscreen, which is the one people guess wrong
    // because a double-tap in the middle of a video is a zoom in most apps.
    @Test
    fun twoTapsInTheCentreIsFullscreen() {
        assertEquals(TouchAction.TOGGLE_FULLSCREEN, doubleTapAction(TouchZone.PLAY_PAUSE))
    }

    // The numbers a viewer feels. A different threshold is a player that
    // responds differently under the same finger.
    @Test
    fun theWebsTimingsAndStepAreKept() {
        assertEquals(300L, DOUBLE_TAP_THRESHOLD_MS)
        assertEquals(10.0, TOUCH_SEEK_SECONDS)
    }

    @Test
    fun aTapOnTheExactEdgeStaysInBounds() {
        assertEquals(TouchZone.SEEK_BACK, zoneAt(x = 0f, y = 0f, volumeZones = false))
        assertEquals(TouchZone.SEEK_FORWARD, zoneAt(x = 1f, y = 1f, volumeZones = false))
    }
}
