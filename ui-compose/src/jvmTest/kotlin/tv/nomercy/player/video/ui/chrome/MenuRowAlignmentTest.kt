// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.chrome.menus.LABEL_TAG_SUFFIX
import tv.nomercy.player.video.ui.chrome.menus.ROW_SPEED
import kotlin.test.Test
import kotlin.test.assertEquals

// One list, one left edge.
//
// The chosen row's mark was a bullet written into the label STRING, so the row
// a viewer is already on started a character further right than every row above
// and below it. The web draws the checkmark glyph in its own
// `.menu-button-check` span, which is a column the row reserves whether or not
// it is the chosen one.
@OptIn(ExperimentalTestApi::class)
class MenuRowAlignmentTest {

    private fun ComposeUiTest.openSpeedPane() {
        val player = NMVideoPlayer(RecordingVideoBackend())

        setContent {
            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestItem()))
                player.load(ChromeTestItem())
                player.play()
            }
            VideoChrome(player, FormFactor.Desktop)
        }
        waitForIdle()
        mainClock.autoAdvance = false

        onNodeWithTag(SETTINGS_TAG).performClick()
        mainClock.advanceTimeBy(SETTLE)
        waitForIdle()
        onNodeWithTag(ROW_SPEED).performClick()
        mainClock.advanceTimeBy(SETTLE)
        waitForIdle()
    }

    private fun ComposeUiTest.labelLeft(row: String): Dp =
        onNodeWithTag(row + LABEL_TAG_SUFFIX, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
            .left

    @Test
    fun theChosenSpeedStartsWhereEveryOtherSpeedStarts() = runComposeUiTest {
        openSpeedPane()

        // Normal is the speed a player starts at, so it is the checked row, and
        // the two either side of it are not.
        val chosen: Dp = labelLeft(SPEED_ROW_NORMAL)
        val slower: Dp = labelLeft(SPEED_ROW_SLOWER)
        val faster: Dp = labelLeft(SPEED_ROW_FASTER)

        assertEquals(slower, chosen, "the checked row is inset from the rest of the list")
        assertEquals(slower, faster, "two unchecked rows disagree with each other")
    }
}

private const val SETTLE = 600L

// The speed pane's rows, named by the value they set rather than by position.
private const val SPEED_ROW_NORMAL = "nm-speed-1.0"
private const val SPEED_ROW_SLOWER = "nm-speed-0.75"
private const val SPEED_ROW_FASTER = "nm-speed-1.25"
