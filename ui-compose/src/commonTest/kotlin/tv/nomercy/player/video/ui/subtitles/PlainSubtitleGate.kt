// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.ALIGN_CENTER
import tv.nomercy.player.core.events.ALIGN_START
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitleCue
import tv.nomercy.player.core.events.SubtitleCueChange
import tv.nomercy.player.video.ui.RecordingBackend
import kotlin.test.Test

// What a plain-text caption track actually puts on the picture.
//
// `SubtitlePlugin` only draws ASS/SSA, so every WebVTT and SRT sidecar reached
// the shared cue channel with nothing listening and the viewer saw no subtitles
// at all. These assert on the drawn text, not on the subscription: the bug was
// a layer that existed and rendered nothing.
//
// Abstract and subclassed per host, the same shape as PlayerControlsGate — a
// Robolectric runner cannot be named from common code.
@OptIn(ExperimentalTestApi::class)
abstract class PlainSubtitleGate {

    private fun player(): ComposedPlayer = ComposedPlayer(backend = RecordingBackend())

    private fun cue(text: String, align: String = ALIGN_CENTER) = SubtitleCue(
        text = text,
        plainText = text,
        align = align,
    )

    private fun ComposeUiTest.showing(player: ComposedPlayer, cues: List<SubtitleCue>) {
        setContent { PlainSubtitleLayer(player = player, modifier = Modifier) }
        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(cues))
        waitForIdle()
    }

    @Test
    fun aPlainCueIsDrawn() = runComposeUiTest {
        val player = player()
        showing(player, listOf(cue("You should have brought an army.")))

        onNodeWithText("You should have brought an army.").assertIsDisplayed()
    }

    @Test
    fun everyLineOfTheActiveSetIsDrawn() = runComposeUiTest {
        val player = player()
        showing(player, listOf(cue("First line."), cue("Second line.")))

        onNodeWithText("First line.").assertIsDisplayed()
        onNodeWithText("Second line.").assertIsDisplayed()
    }

    @Test
    fun anAlignedCueIsStillDrawn() = runComposeUiTest {
        val player = player()
        showing(player, listOf(cue("Off to the side.", align = ALIGN_START)))

        onNodeWithText("Off to the side.").assertIsDisplayed()
    }

    // Leaving a cue has to take it off the picture; a renderer still showing the
    // previous line is worse than a blank one.
    @Test
    fun anEmptySetClearsThePicture() = runComposeUiTest {
        val player = player()
        showing(player, listOf(cue("Gone in a moment.")))

        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(emptyList()))
        waitForIdle()

        onNodeWithText("Gone in a moment.").assertDoesNotExist()
    }
}
