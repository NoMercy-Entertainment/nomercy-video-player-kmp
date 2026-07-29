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
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.test.Test

// That the bar OBEYS the responsive rule, rather than that the rule is correct.
//
// ChromeResponsiveTest and WebRenderParityTest already grade the rule against
// the browser at nine widths, and both passed for a rule the bar never called:
// visibleControls was ported, corrected from a rank cut to a width accumulation,
// asserted against a real export and gated in CI, and `grep -rl visibleControls`
// returned one file — its own. A tested rule with no caller is a phone bar that
// overflows and drops the fullscreen button off the right edge while every gate
// stays green.
//
// So these two cases go through the assembled chrome at a real width and ask the
// screen. There is no way to satisfy them except by the bar consulting the rule.
@OptIn(ExperimentalTestApi::class)
abstract class ChromeWidthGate {

    private fun ComposeUiTest.mountAt(widthDp: Int) {
        setContent {
            val player: NMVideoPlayer = NMVideoPlayer(RecordingVideoBackend())

            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestItem()))
                player.load(ChromeTestItem())
            }

            // A fixed box rather than the test's own window, because the
            // window size differs per host and the whole point is a known
            // width. Plain width, coerced into what the host hands down: a
            // requiredWidth would put the bar outside the window and every
            // node in it would stop being clickable. The Android host declares
            // a wide device through a Robolectric qualifier instead.
            Box(modifier = Modifier.width(widthDp.dp).height(WINDOW_HEIGHT.dp)) {
                VideoChrome(player, FormFactor.Phone)
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
    }

    @Test
    fun aPhoneWidthDropsTheControlsThatDoNotFit() = runComposeUiTest {
        // 360 leaves 212 after the reserved width. Play costs 40 and the volume
        // control costs 136 because the slider expands beside it, so fullscreen
        // — ranked fourth — is the first thing that will not fit.
        mountAt(PHONE_WIDTH)

        onNodeWithTag(PLAY_PAUSE_TAG).assertIsDisplayed()
        onNodeWithTag(FULLSCREEN_TAG).assertDoesNotExist()
    }

    @Test
    fun aDesktopWidthKeepsThemAll() = runComposeUiTest {
        mountAt(DESKTOP_WIDTH)

        onNodeWithTag(PLAY_PAUSE_TAG).assertIsDisplayed()
        onNodeWithTag(FULLSCREEN_TAG).assertIsDisplayed()
        onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
    }
}

private const val PHONE_WIDTH = 360
private const val DESKTOP_WIDTH = 1280
private const val WINDOW_HEIGHT = 640
