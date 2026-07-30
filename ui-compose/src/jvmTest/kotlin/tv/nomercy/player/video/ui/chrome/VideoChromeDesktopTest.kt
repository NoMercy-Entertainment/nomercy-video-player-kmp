// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

// The two things only a desktop has: a pointer that comes and goes, and a
// keyboard.
//
// Desktop-only rather than in the shared gate, because a phone has neither and a
// test that pretended otherwise would be asserting against synthesised events no
// viewer can produce.
@OptIn(ExperimentalTestApi::class)
class VideoChromeDesktopTest {

    private fun ComposeUiTest.mountPlaying(): NMVideoPlayer {
        val player = NMVideoPlayer(RecordingVideoBackend())

        setContent {
            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestItem()))
                player.load(ChromeTestItem())
                // Playing, because three of the five autohide rules do nothing
                // at all while a film is paused: paused holds the chrome open,
                // and a test that left it paused would pass on that instead.
                player.play()
            }
            VideoChrome(player, FormFactor.Desktop)
        }
        waitForIdle()

        // The autohide is four seconds and a running test clock would step
        // straight through it, so a chrome woken by the pointer would be gone
        // again before the assertion looked at it.
        mainClock.autoAdvance = false
        return player
    }

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
    }

    @Test
    fun movingThePointerWakesTheChrome() = runComposeUiTest {
        mountPlaying()

        onNodeWithTag(DESKTOP_CHROME_TAG).performMouseInput { moveTo(center) }
        settle()

        onNodeWithTag(TRANSPORT_BAR_TAG).assertExists()
    }

    @Test
    fun andLeavingTheWindowTakesItAway() = runComposeUiTest {
        // Rule three. A pointer leaving is a stronger signal than one that has
        // merely stopped moving, so it does not wait out the four seconds.
        mountPlaying()

        onNodeWithTag(DESKTOP_CHROME_TAG).performMouseInput { moveTo(center) }
        settle()
        onNodeWithTag(DESKTOP_CHROME_TAG).performMouseInput { moveTo(Offset(-1f, -1f)) }
        settle()

        onNodeWithTag(TRANSPORT_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun aPointerRestingOnTheBarIsShownWhereItWouldLand() = runComposeUiTest {
        // `wireSliderBar` shows `.slider-pop` on `mouseover`, not only during a
        // drag. Without it a viewer with a mouse had to commit to a drag before the
        // player would tell them anything about where they were pointing.
        mountPlaying()

        onNodeWithTag(SCRUBBER_TAG).performMouseInput { moveTo(center) }
        settle()

        onNodeWithTag(SCRUB_PREVIEW_TAG).assertExists()
    }

    @Test
    fun andTheBubbleGoesWhenThePointerLeavesTheBar() = runComposeUiTest {
        // `mouseleave` sets `--visibility: 0` and resets every chapter marker's
        // hover fill to scaleX(0). Both stayed put, so the bar went on advertising a
        // position nobody was pointing at.
        //
        // Here rather than in the shared gate for the reason at the top of this
        // file, and it was measured rather than assumed: Robolectric's hover
        // synthesis lands the enter and drops the exit, so the Android host had the
        // bubble still up after a pointer had left. That is the emulator's account
        // of a mouse, not the chrome's behaviour.
        mountPlaying()

        onNodeWithTag(SCRUBBER_TAG).performMouseInput { moveTo(center) }
        settle()
        onNodeWithTag(SCRUBBER_TAG).performMouseInput { exit(center) }
        settle()

        onNodeWithTag(SCRUB_PREVIEW_TAG).assertDoesNotExist()
    }

    @Test
    fun spaceTogglesPlayback() = runComposeUiTest {
        // Through the key handler rather than through a binding of the view's
        // own, which is what keeps the desktop keys and the television keys the
        // same table with one group swapped.
        val player = mountPlaying()

        onNodeWithTag(DESKTOP_CHROME_TAG).requestFocus()
        onNodeWithTag(DESKTOP_CHROME_TAG).performKeyInput { pressKey(Key.Spacebar) }
        settle()

        assertEquals(PlayState.PAUSED, player.state().playState)
    }
}

private const val SETTLE_MS = 500L
