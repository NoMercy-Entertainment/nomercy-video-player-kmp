// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.click
import org.junit.Rule
import org.junit.Test
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.ui.tv.Cancellable
import tv.nomercy.player.video.ui.tv.Scheduler
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Tapping the picture, on a device.
//
// A gesture test is only meaningful where gestures are real. The double-tap
// window, the press-then-tap ordering and the way a tap resolves are all the
// framework's timing rather than something a shim reproduces.
class TouchZonesOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recording : ChromeCommands {
        val seeks: MutableList<Float> = mutableListOf()
        var fullscreen: Boolean? = null
        var playing: Boolean? = null

        override fun seekTo(seconds: Double) = Unit
        override fun seekBy(deltaSeconds: Float) { seeks += deltaSeconds }
        override fun setPlaying(playing: Boolean) { this.playing = playing }
        override fun next() = Unit
        override fun previous() = Unit
        override fun openAudioMenu() = Unit
        override fun openSubtitleMenu() = Unit
        override fun setVolume(percent: Int) = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun selectQuality(level: QualityLevel?) = Unit
        override fun selectAudioTrack(track: AudioTrack) = Unit
        override fun selectSubtitleTrack(track: SubtitleTrack?) = Unit
        override fun setRate(rate: Float) = Unit
        override fun setFullscreen(fullscreen: Boolean) { this.fullscreen = fullscreen }
        override fun dismissMessage() = Unit
    }

    private val commands = Recording()
    private var clock: Long = 0

    // A single tap is held back until the double-tap window has passed, so the
    // clock has to be moved for it to arrive at all. Without this the assertions
    // that follow a click run before anything has happened and pass on nothing,
    // which is how two of these were green while the third was not.
    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(TAP_SETTLE_MS)
        compose.waitForIdle()
    }

    private fun mount(startVisible: Boolean): ChromeController {
        val controller = ChromeController({ true }, Scheduler { _, _ -> Cancellable { } })
        if (startVisible) controller.bumpActivity()

        compose.setContent {
            TouchZonesOverlay(controller, commands, nowMs = { clock })
        }
        return controller
    }

    @Test
    fun aDoubleTapOnTheRightSkipsForward() {
        mount(startVisible = false)

        compose.onNodeWithTag(ZONE_FORWARD).performTouchInput { doubleClick() }

        assertEquals(listOf(STEP), commands.seeks)
    }

    @Test
    fun aDoubleTapOnTheLeftSkipsBack() {
        mount(startVisible = false)

        compose.onNodeWithTag(ZONE_BACK).performTouchInput { doubleClick() }

        assertEquals(listOf(-STEP), commands.seeks)
    }

    @Test
    fun theIndicatorSaysHowFarTheRunHasGone() {
        // Four separate flashes of "10 seconds" tell a viewer nothing about
        // where they have got to.
        mount(startVisible = false)

        compose.onNodeWithTag(ZONE_FORWARD).performTouchInput { doubleClick() }

        compose.onNodeWithTag(INDICATOR_FORWARD).assertIsDisplayed()
    }

    @Test
    fun theIndicatorPicksTheSideThatWasTapped() {
        // On the wrong side it reads as the other direction, which on a control
        // with no label is the entire message.
        mount(startVisible = false)

        compose.onNodeWithTag(ZONE_BACK).performTouchInput { doubleClick() }

        compose.onNodeWithTag(INDICATOR_BACK).assertIsDisplayed()
        compose.onNodeWithTag(INDICATOR_FORWARD).assertDoesNotExist()
    }

    @Test
    fun aDoubleTapInTheMiddleFillsTheScreen() {
        // Where a viewer taps to make the picture bigger, in every player they
        // have used.
        mount(startVisible = false)

        compose.onNodeWithTag(ZONE_CENTRE).performTouchInput { doubleClick() }

        assertEquals(true, commands.fullscreen)
    }

    @Test
    fun aTapOnHiddenChromeRevealsItWithoutPausing() {
        // The race the snapshot exists for. The surface wakes on the way down,
        // and acting on the tap as well would hide what it just revealed and
        // pause the film on the way.
        val controller: ChromeController = mount(startVisible = false)

        tap(ZONE_CENTRE)

        assertTrue(controller.ui.value.active)
        assertEquals(null, commands.playing)
    }

    @Test
    fun aTapOnVisibleChromePausesAndHides() {
        val controller: ChromeController = mount(startVisible = true)

        tap(ZONE_CENTRE)

        assertEquals(false, commands.playing)
        assertTrue(!controller.ui.value.active)
    }

    @Test
    fun aTapOnASideZoneNeverPauses() {
        // The sides are for skipping. Pausing from one is a viewer losing the
        // film because they aimed slightly wide.
        mount(startVisible = true)

        tap(ZONE_FORWARD)

        assertEquals(null, commands.playing)
    }
}

private const val TAP_SETTLE_MS = 1_000L
private const val STEP = 10f
