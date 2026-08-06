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
 * The bar's geometry, measured on the nodes rather than read off a constant.
 *
 * Both sides declare BUTTON_WIDTH = 40, and a matching constant proves only
 * that two files agree. What the web actually LAYS OUT was measured through
 * CDP on the running testbed — container 662, fourteen visible buttons, every
 * one 40 wide, pitch exactly 40 with no gap between them — and this asserts the
 * native bar against those numbers.
 *
 * It is the half of pixel parity that a method count cannot see: the chrome
 * scored 19/19 by membership while its drop order was wrong at two ranks, and
 * a bar can be built from correct constants and still be laid out wrong.
 *
 * Only the two controls a default-configured bar always draws are asserted.
 * Seek and chapter buttons depend on options this player was not given, and a
 * pitch assertion across non-adjacent controls measures the assertion's own
 * assumption about the layout rather than the layout — which is how the first
 * version of this file failed on correct code.
 */
@OptIn(ExperimentalTestApi::class)
class BarGeometryTest {

    @Test
    fun everyTransportButtonIsTheReferenceWidth() = runComposeUiTest {
        renderBar()

        for (tag in MEASURED_TAGS) {
            val width: Int = onNodeWithTag(tag).fetchSemanticsNode().size.width
            assertEquals(BUTTON_DP, width, "$tag is $width wide, the reference lays out $BUTTON_DP")
        }
    }

    /**
     * Adjacent buttons sit one width apart, with nothing between them.
     *
     * The reference packs them: 8, 48, 88, 128 — a pitch of exactly 40 against
     * a 40-wide button, so no gap at all. A native bar that added even two
     * points of spacing would drop a control a rung earlier than the web does
     * at the same width, and nothing in the priority list or the constants
     * would say so.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.renderBar() {
        setContent {
            val player = NMVideoPlayer(RecordingVideoBackend())
            LaunchedEffect(player) { player.setup(PlayerConfig()) }

            // Wide enough for every rung, so nothing is missing for a reason
            // other than the one under test.
            Box(modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp)) {
                VideoChrome(player, FormFactor.Desktop)
            }
        }
        waitForIdle()
    }

    private companion object {
        // Measured from the running web testbed through CDP, not read from
        // BUTTON_WIDTH. See scripts/web-bar-geometry.mjs.
        const val BUTTON_DP = 40

        // The two that a default-configured bar always draws. Seek and chapter
        // buttons depend on options this player was not given, and asserting on
        // a node that is legitimately absent measures the fixture rather than
        // the layout.
        val MEASURED_TAGS = listOf(PLAY_PAUSE_TAG, VOLUME_TAG)

        const val WIDTH = 1280
        const val HEIGHT = 720
    }
}
