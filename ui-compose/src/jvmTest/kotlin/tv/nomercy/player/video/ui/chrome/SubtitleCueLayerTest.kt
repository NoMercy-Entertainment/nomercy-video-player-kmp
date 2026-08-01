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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.core.events.SubtitleCue
import tv.nomercy.player.video.subtitles.SubtitleOverlayPlugin
import kotlin.test.Test

/**
 * The cues, on screen.
 *
 * Graded through the whole chrome rather than by calling the layer directly,
 * because what was wrong was never the layer: `SubtitleOverlayPlugin` laid the
 * boxes out correctly the whole time and no composable read them, so the ported
 * geometry, the ported style and the settings menu all worked and the viewer saw
 * nothing. A test that mounted the layer by hand would have passed on the
 * unwired build.
 */
@OptIn(ExperimentalTestApi::class)
class SubtitleCueLayerTest {

    // Registered from an effect, which is where an app registers it: addPlugin
    // suspends, so it cannot happen before the first composition. A chrome that
    // looked the plugin up once and remembered the answer would find nothing
    // here and draw nothing for the life of the screen.
    private fun ComposeUiTest.mount(overlay: SubtitleOverlayPlugin?): NMVideoPlayer {
        val player = NMVideoPlayer(RecordingVideoBackend())

        setContent {
            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                overlay?.let { player.addPlugin(it) }
            }

            Box(modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp)) {
                VideoChrome(player, FormFactor.Desktop)
            }
        }
        waitForIdle()

        return player
    }

    @Test
    fun aCueTheOverlayLaidOutIsDrawn() = runComposeUiTest {
        val overlay = SubtitleOverlayPlugin()
        mount(overlay)

        overlay.show(listOf(SubtitleCue(text = LINE, plainText = LINE)))
        waitForIdle()

        onNodeWithTag(SUBTITLE_LAYER_TAG).assertIsDisplayed()
        onNodeWithText(LINE).assertIsDisplayed()
    }

    @Test
    fun twoAtOnceBothAppear() = runComposeUiTest {
        // Two cues active together is a sign and a line of dialogue, and the
        // layout pushes them apart rather than stacking them. Drawing one would
        // look like the other having failed to arrive.
        val overlay = SubtitleOverlayPlugin()
        mount(overlay)

        overlay.show(
            listOf(
                SubtitleCue(text = LINE, plainText = LINE, line = 90.0),
                SubtitleCue(text = SIGN, plainText = SIGN, line = 90.0),
            ),
        )
        waitForIdle()

        onNodeWithText(LINE).assertIsDisplayed()
        onNodeWithText(SIGN).assertIsDisplayed()
    }

    @Test
    fun andTheyGoWhenTheOverlayClears() = runComposeUiTest {
        val overlay = SubtitleOverlayPlugin()
        mount(overlay)

        overlay.show(listOf(SubtitleCue(text = LINE, plainText = LINE)))
        waitForIdle()
        overlay.clear()
        waitForIdle()

        onNodeWithText(LINE).assertDoesNotExist()
        onNodeWithTag(SUBTITLE_LAYER_TAG).assertDoesNotExist()
    }

    @Test
    fun theDefaultsSevenShadowCopiesAreStillOneLineToARead() = runComposeUiTest {
        // `textShadow` is the default and it stacks the same text seven times.
        // Left as seven semantics nodes, a screen reader announces every caption
        // in the player seven times — so the copies under the top one are the
        // shadow and say nothing. `onNodeWithText` asserting a single node is
        // the check: it fails outright when more than one matches.
        val overlay = SubtitleOverlayPlugin()
        mount(overlay)

        overlay.show(listOf(SubtitleCue(text = LINE, plainText = LINE)))
        waitForIdle()

        onNodeWithText(LINE).assertIsDisplayed()
    }

    @Test
    fun aPlayerWithNoOverlayDrawsNoLayerAtAll() = runComposeUiTest {
        // Not an empty layer over the picture — nothing. A player whose
        // subtitles are painted by the libass module has its own renderer, and a
        // second transparent one on top is a hit-test target for no reason.
        mount(overlay = null)

        onNodeWithTag(SUBTITLE_LAYER_TAG).assertDoesNotExist()
    }
}

private const val LINE = "It followed us here."
private const val SIGN = "— NORTH GATE —"

private const val WIDTH = 1280
private const val HEIGHT = 720
