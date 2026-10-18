// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import tv.nomercy.player.video.DisplayMessage
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.VideoEvents
import tv.nomercy.player.video.ui.tv.tvChromeStrings

@OptIn(ExperimentalTestApi::class)
class ChromeMessageTimerTest {
    @Test
    fun theSameHostNoticeGetsItsFullWindowAgain() = runComposeUiTest {
        mainClock.autoAdvance = false
        val player = NMVideoPlayer(RecordingVideoBackend())
        setContent {
            BasicText(rememberChromeMessage(player, tvChromeStrings("en"))?.text ?: "No notice")
        }
        waitForIdle()

        runOnIdle { player.emit(VideoEvents.DisplayMessage, DisplayMessage("Saved", 1_000.0)) }
        mainClock.advanceTimeByFrame()
        onNodeWithText("Saved").assertIsDisplayed()
        mainClock.advanceTimeBy(600)

        runOnIdle { player.emit(VideoEvents.DisplayMessage, DisplayMessage("Saved", 1_000.0)) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(500)

        onNodeWithText("Saved").assertIsDisplayed()
    }
}
