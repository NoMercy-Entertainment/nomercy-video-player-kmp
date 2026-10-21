// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------
package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The top bar's buttons sit as far from the top edge as the transport buttons sit
 * from the bottom edge (Stoney, 2026-10-21: the top bar "needs to mirror the bottom bar").
 */
@OptIn(ExperimentalTestApi::class)
class BarsMirrorTest {
    @Test
    fun theTopButtonsAreAsFarFromTheTopAsTheBottomButtonsFromTheBottom() = runComposeUiTest {
        val player = NMVideoPlayer(RecordingVideoBackend())
        setContent {
            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestEpisode(), ChromeTestEpisode(id = "two")))
                player.load(ChromeTestEpisode())
            }
            Box(modifier = Modifier.width(1280.dp).height(720.dp).background(Color.Black)) {
                VideoChrome(player, FormFactor.Desktop, onClose = {})
            }
        }
        waitForIdle()
        onNodeWithTag(DESKTOP_CHROME_TAG).performMouseInput { moveTo(center) }
        waitForIdle()

        val frame = onRoot().fetchSemanticsNode().boundsInRoot
        val close = onNodeWithTag(CLOSE_TAG).fetchSemanticsNode().boundsInRoot
        val play = onNodeWithTag(PLAY_PAUSE_TAG).fetchSemanticsNode().boundsInRoot
        val topGap = close.top - frame.top
        val bottomGap = frame.bottom - play.bottom

        assertTrue(
            abs(topGap - bottomGap) <= 1f,
            "top buttons ${topGap}px from the top, play ${bottomGap}px from the bottom",
        )
    }
}
