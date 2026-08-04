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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A round button is a SQUARE box with a circular shape on it.
 *
 * CircleShape over a 40x32 box is an oval, and Modifier.size() is still coerced
 * by the parent — so a 40dp button inside a shorter bar row measured 40 wide and
 * whatever the row allowed. Every hover and every focus ring in both bars drew
 * an oval, which is what Stoney was looking at while the shape constant said
 * CircleShape and the web's computed radius said 9999px.
 *
 * Measured on the node, because the constant was never the thing that was wrong.
 */
@OptIn(ExperimentalTestApi::class)
class BarButtonRoundnessTest {

    @Test
    fun aTransportButtonIsSquareSoItsCircleIsACircle() = runComposeUiTest {
        setContent {
            val player = NMVideoPlayer(RecordingVideoBackend())
            LaunchedEffect(player) { player.setup(PlayerConfig()) }

            // Deliberately shorter than the button. A row that fits is not the
            // case that broke; a row that does not is.
            Box(modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp)) {
                VideoChrome(player, FormFactor.Desktop)
            }
        }
        waitForIdle()

        val bounds = onNodeWithTag(PLAY_PAUSE_TAG).fetchSemanticsNode().size

        assertEquals(bounds.width, bounds.height, "a circle needs a square box")
    }
}

private const val WIDTH = 640

private const val HEIGHT = 200
