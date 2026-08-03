// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import tv.nomercy.player.video.tv.Cancellable
import tv.nomercy.player.video.tv.Scheduler
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Tapping the picture, on a device.
//
// A gesture test is only meaningful where gestures are real. The double-tap
// window, the press-then-tap ordering and the way a tap resolves are all the
// framework's timing rather than something a shim reproduces.
//
// By POSITION rather than by a per-zone test tag. The overlay is one hit test
// over the whole picture and the zone is arithmetic, so a tag per column would
// be testing a layout that no longer decides anything — and it is exactly what
// let the volume zones stay missing while every assertion here passed.
class TouchZonesOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private val commands = RecordingChromeCommands()
    private var clock: Long = 0

    private fun zones() = compose.onNodeWithTag(TOUCH_ZONES_TAG)

    private fun doubleTapAt(x: Float, y: Float) {
        zones().performTouchInput { doubleClick(percentOffset(x, y)) }
        compose.waitForIdle()
    }

    // A single tap is held back until the double-tap window has passed, so the
    // clock has to be moved for it to arrive at all. Without this the assertions
    // that follow a click run before anything has happened and pass on nothing,
    // which is how two of these were green while the third was not.
    private fun tapAt(x: Float, y: Float) {
        zones().performTouchInput { click(percentOffset(x, y)) }
        compose.mainClock.advanceTimeBy(TAP_SETTLE_MS)
        compose.waitForIdle()
    }

    private fun mount(
        startVisible: Boolean,
        state: ChromeState = ChromeState(),
    ): ChromeController {
        val controller = ChromeController({ true }, Scheduler { _, _ -> Cancellable { } })
        if (startVisible) controller.bumpActivity()

        compose.setContent {
            TouchZonesOverlay(state, controller, commands, nowMs = { clock })
        }
        return controller
    }

    @Test
    fun aDoubleTapOnTheRightSkipsForward() {
        mount(startVisible = false)

        doubleTapAt(RIGHT, MIDDLE)

        assertEquals(listOf(STEP), commands.seeks)
    }

    @Test
    fun aDoubleTapOnTheLeftSkipsBack() {
        mount(startVisible = false)

        doubleTapAt(LEFT, MIDDLE)

        assertEquals(listOf(-STEP), commands.seeks)
    }

    @Test
    fun theIndicatorSaysHowFarTheRunHasGone() {
        // Four separate flashes of "10 seconds" tell a viewer nothing about
        // where they have got to.
        mount(startVisible = false)

        doubleTapAt(RIGHT, MIDDLE)

        compose.onNodeWithTag(INDICATOR_FORWARD).assertIsDisplayed()
    }

    @Test
    fun theIndicatorPicksTheSideThatWasTapped() {
        // On the wrong side it reads as the other direction, which on a control
        // with no label is the entire message.
        mount(startVisible = false)

        doubleTapAt(LEFT, MIDDLE)

        compose.onNodeWithTag(INDICATOR_BACK).assertIsDisplayed()
        compose.onNodeWithTag(INDICATOR_FORWARD).assertDoesNotExist()
    }

    @Test
    fun aTapInTheMiddleTogglesPlaybackWhicheverWayItWasGoing() {
        // Unconditional, per the web's centre-zone contract, and a TOGGLE. This
        // asserted the opposite — that a tap on hidden chrome must not touch
        // playback — which is the guard the web source carries a comment about
        // being wrong: a cold tap on a phone always finds the controls hidden,
        // so the middle of the picture silently did nothing.
        mount(startVisible = false, state = ChromeState(playing = false))

        tapAt(CENTRE, MIDDLE)

        assertEquals(true, commands.playing)
    }

    @Test
    fun aTapOnVisibleChromePausesAndHides() {
        val controller: ChromeController = mount(startVisible = true, state = ChromeState(playing = true))

        tapAt(CENTRE, MIDDLE)

        assertEquals(false, commands.playing)
        assertTrue(!controller.ui.value.active)
    }

    @Test
    fun aTapOnASideZoneNeverPauses() {
        // The sides are for skipping. Pausing from one is a viewer losing the
        // film because they aimed slightly wide.
        mount(startVisible = true)

        tapAt(RIGHT, MIDDLE)

        assertEquals(null, commands.playing)
    }

    @Test
    fun aDoubleTapAtTheTopOfTheMiddleTurnsTheVolumeUp() {
        // The zone that had nowhere to land. A finger has no wheel, so this is
        // the only way to change the volume without opening the bar.
        mount(startVisible = false, state = ChromeState(volume = SOME_VOLUME))

        doubleTapAt(CENTRE, TOP)

        assertEquals(SOME_VOLUME + STEP_PERCENT, commands.volume)
    }

    @Test
    fun theVolumeStopsAtTheEnds() {
        // A step past full is a number the bar cannot draw and the engine
        // rejects, and it arrives after two taps rather than as an edge case.
        mount(startVisible = false, state = ChromeState(volume = NEARLY_FULL_VOLUME))

        doubleTapAt(CENTRE, TOP)

        assertEquals(FULL_VOLUME, commands.volume)
    }

    @Test
    fun aSingleTapOnAVolumeZoneOnlyTouchesTheControls() {
        mount(startVisible = false, state = ChromeState(volume = SOME_VOLUME))

        tapAt(CENTRE, TOP)

        assertEquals(null, commands.volume)
        assertEquals(null, commands.playing)
    }

}

private const val TAP_SETTLE_MS = 1_000L
private const val STEP = 10f

// Anywhere clear of both ends, so a case about a step is not also a case about
// clamping.
private const val SOME_VOLUME = 40
private const val STEP_PERCENT = 5
private const val NEARLY_FULL_VOLUME = 98
private const val FULL_VOLUME = 100

// Fractions of the surface, matching the three columns and the six rows.
private const val LEFT = 0.1f
private const val CENTRE = 0.5f
private const val RIGHT = 0.9f
private const val TOP = 0.05f
private const val MIDDLE = 0.5f
