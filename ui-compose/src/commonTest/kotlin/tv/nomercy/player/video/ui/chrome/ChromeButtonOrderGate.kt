// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.test.Test
import kotlin.test.assertTrue

// That a consumer's `buttonOrder` actually moves the buttons.
//
// The option is what his own site passes and it was not a field at all: the
// class named it in a comment describing what the web takes, so the same
// configuration carried across from the web player compiled — an unknown named
// argument is a compile error, an absent one is silence — and drew the default
// order. Nothing could have noticed, because every gate compared the DEFAULT bar
// against the web's default bar.
//
// Asserted on where the buttons END UP rather than on the list that was passed
// in. A test that checked the computed order would pass on a bar that ignored
// it, which is the failure being fixed.
@OptIn(ExperimentalTestApi::class)
abstract class ChromeButtonOrderGate {

    private fun ComposeUiTest.mountWith(order: List<ChromeControl>) {
        setContent {
            val player: NMVideoPlayer = NMVideoPlayer(RecordingVideoBackend())

            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestItem()))
                player.load(ChromeTestItem())
            }

            Box(modifier = Modifier.width(DESKTOP_WIDTH.dp).height(WINDOW_HEIGHT.dp)) {
                VideoChrome(player, FormFactor.Desktop, layout = ChromeLayout(buttonOrder = order))
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
    }

    @Test
    fun withoutAnOrderPlayLeadsTheRowAndFullscreenEndsIt() = runComposeUiTest {
        mountWith(emptyList())

        val play = onNodeWithTag(PLAY_PAUSE_TAG).getUnclippedBoundsInRoot().left
        val fullscreen = onNodeWithTag(FULLSCREEN_TAG).getUnclippedBoundsInRoot().left

        assertTrue(play < fullscreen, "the web's own order: play first, fullscreen last")
    }

    // The rule the web states: a named control is re-anchored to the END of the
    // row, which means it crosses the divider and lands after fullscreen.
    @Test
    fun aNamedControlMovesToTheEndOfTheRow() = runComposeUiTest {
        mountWith(listOf(ChromeControl.PLAY))

        val play = onNodeWithTag(PLAY_PAUSE_TAG).getUnclippedBoundsInRoot().left
        val fullscreen = onNodeWithTag(FULLSCREEN_TAG).getUnclippedBoundsInRoot().left

        onNodeWithTag(PLAY_PAUSE_TAG).assertIsDisplayed()
        assertTrue(play > fullscreen, "play was named, so it belongs after fullscreen — drew at $play")
    }

    // Named controls land in the sequence given, not in the bar's own order.
    @Test
    fun theTailIsInTheOrderTheConsumerAsked() = runComposeUiTest {
        mountWith(listOf(ChromeControl.SETTINGS, ChromeControl.PLAY))

        val settings = onNodeWithTag(SETTINGS_TAG).getUnclippedBoundsInRoot().left
        val play = onNodeWithTag(PLAY_PAUSE_TAG).getUnclippedBoundsInRoot().left

        assertTrue(settings < play, "settings was named first, so it precedes play — $settings vs $play")
    }

    // Everything unnamed stays where the web's builder put it, which is the half
    // of the rule an implementation is most likely to drop: moving the named
    // ones is the visible part, leaving the rest alone is the correct part.
    @Test
    fun anUnnamedControlKeepsItsNaturalPosition() = runComposeUiTest {
        mountWith(listOf(ChromeControl.PLAY))

        val settings = onNodeWithTag(SETTINGS_TAG).getUnclippedBoundsInRoot().left
        val fullscreen = onNodeWithTag(FULLSCREEN_TAG).getUnclippedBoundsInRoot().left

        assertTrue(settings < fullscreen, "neither was named, so both keep the web's order")
    }
}

private const val DESKTOP_WIDTH = 1280
private const val WINDOW_HEIGHT = 640
