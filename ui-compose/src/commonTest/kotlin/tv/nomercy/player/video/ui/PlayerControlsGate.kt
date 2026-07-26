// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerConfig
import kotlin.test.Test
import kotlin.test.assertEquals

// The drop-in control against a real player.
//
// Nothing here asserts that a composable rendered. It asserts that clicking the
// button changed what the engine is doing, and that what the engine is doing
// changed the button — which is the only claim worth making about a control,
// and the one that breaks when the wiring is subtly wrong.
//
// Abstract, and subclassed once per host. Compose's Android test host runs under
// Robolectric, which needs its runner named on the class, and a runner cannot be
// named from common code. Writing the gate once and naming the host twice is the
// price; running two different gates and calling it cross-platform would be
// worse.
@OptIn(ExperimentalTestApi::class)
abstract class PlayerControlsGate {

    private fun player(): ComposedPlayer = ComposedPlayer(backend = RecordingBackend())

    // setup() suspends, and a Compose test body does not. LaunchedEffect is the
    // scope already on hand, and running it there also means the player is set
    // up exactly the way an app sets it up: inside the composition that mounts
    // it, not on some thread the app had to find first.
    private fun ComposeUiTest.mount(player: ComposedPlayer) {
        setContent {
            LaunchedEffect(player) { player.setup(PlayerConfig()) }
            PlayerControls(player)
        }
        waitForIdle()
    }

    @Test
    fun clickingTheControlStartsTheEngine() = runComposeUiTest {
        val player = player()
        mount(player)

        onNodeWithContentDescription(PLAY_LABEL).performClick()
        waitForIdle()

        assertEquals(PlayState.PLAYING, player.state().playState)
    }

    @Test
    fun clickingItAgainPausesTheEngine() = runComposeUiTest {
        val player = player()
        mount(player)

        onNodeWithContentDescription(PLAY_LABEL).performClick()
        waitForIdle()
        onNodeWithContentDescription(PAUSE_LABEL).performClick()
        waitForIdle()

        assertEquals(PlayState.PAUSED, player.state().playState)
    }

    @Test
    fun theEnginePlayingOnItsOwnRedrawsTheControl() = runComposeUiTest {
        // Nothing touched the button. A player that starts for its own reasons —
        // autoplay, a queue advancing, a remote command — must still be drawn
        // accurately, which a button remembering its own clicks cannot do.
        val player = player()
        mount(player)

        player.play()
        waitForIdle()

        onNodeWithContentDescription(PAUSE_LABEL).assertIsDisplayed()
    }

    @Test
    fun theSelectKeyTogglesPlaybackWithNothingToAimAt() = runComposeUiTest {
        // The ten-foot case. There is no pointer on a television, so the surface
        // takes focus and the select key is the play button.
        val player = player()
        setContent {
            LaunchedEffect(player) { player.setup(PlayerConfig()) }
            Box(Modifier.testTag(SURFACE_TAG).size(SURFACE_SIZE).remoteControl(player))
        }
        waitForIdle()

        onNodeWithTag(SURFACE_TAG).performKeyInput { pressKey(Key.DirectionCenter) }
        waitForIdle()

        assertEquals(PlayState.PLAYING, player.state().playState)
    }

    @Test
    fun theSelectKeyPausesAPlayingPlayerToo() = runComposeUiTest {
        val player = player()
        setContent {
            LaunchedEffect(player) { player.setup(PlayerConfig()) }
            Box(Modifier.testTag(SURFACE_TAG).size(SURFACE_SIZE).remoteControl(player))
        }
        waitForIdle()

        onNodeWithTag(SURFACE_TAG).performKeyInput { pressKey(Key.DirectionCenter) }
        waitForIdle()
        onNodeWithTag(SURFACE_TAG).performKeyInput { pressKey(Key.DirectionCenter) }
        waitForIdle()

        assertEquals(PlayState.PAUSED, player.state().playState)
    }

    @Test
    fun aKeyThatIsNotSelectIsLeftForWhoeverWantsIt() = runComposeUiTest {
        // A player that swallowed every key would break navigation out of
        // itself, which on a television is the difference between a player and
        // a trap.
        val player = player()
        setContent {
            LaunchedEffect(player) { player.setup(PlayerConfig()) }
            Box(Modifier.testTag(SURFACE_TAG).size(SURFACE_SIZE).remoteControl(player))
        }
        waitForIdle()

        onNodeWithTag(SURFACE_TAG).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()

        assertEquals(PlayState.IDLE, player.state().playState)
    }
}

private const val SURFACE_TAG = "surface"
private val SURFACE_SIZE = 200.dp
