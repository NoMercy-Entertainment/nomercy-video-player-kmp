// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.chrome.ChromeCapabilities
import tv.nomercy.player.video.ui.chrome.ChromeTestItem
import tv.nomercy.player.video.ui.chrome.DESKTOP_CHROME_TAG
import tv.nomercy.player.video.ui.chrome.RecordingVideoBackend
import tv.nomercy.player.video.ui.chrome.TOUCH_CHROME_TAG
import tv.nomercy.player.video.ui.tv.ROOT_TAG
import kotlin.test.Test

// Which chrome a device gets.
//
// Four form factors and three chromes, which is the part worth asserting: a
// tablet routing to its own thing would be a third layout nobody maintains, and
// a television routing to the touch chrome would be a set of controls no remote
// can reach. Both are silent failures — the player still plays.
@OptIn(ExperimentalTestApi::class)
abstract class NMVideoPlayerViewRoutingGate {

    private fun ComposeUiTest.mount(formFactor: FormFactor) {
        val player = NMVideoPlayer(RecordingVideoBackend())

        setContent {
            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestItem()))
                player.load(ChromeTestItem())
            }

            NMVideoPlayerView(player, capabilities = ChromeCapabilities(formFactor))
        }
        waitForIdle()
    }

    @Test
    fun aTelevisionGetsTheTenFootChrome() = runComposeUiTest {
        mount(FormFactor.Tv)

        onNodeWithTag(ROOT_TAG).assertExists()
    }

    @Test
    fun aPhoneGetsTheTouchChrome() = runComposeUiTest {
        mount(FormFactor.Phone)

        onNodeWithTag(TOUCH_CHROME_TAG).assertExists()
    }

    @Test
    fun andSoDoesATablet() = runComposeUiTest {
        mount(FormFactor.Tablet)

        onNodeWithTag(TOUCH_CHROME_TAG).assertExists()
    }

    @Test
    fun aDesktopGetsThePointerChrome() = runComposeUiTest {
        mount(FormFactor.Desktop)

        onNodeWithTag(DESKTOP_CHROME_TAG).assertExists()
    }

    @Test
    fun andNoneOfThemGetsTwo() = runComposeUiTest {
        // The failure this catches is a router that falls through: a when with a
        // branch that renders and then carries on renders both, and on a
        // television that is a touch chrome underneath a focus one, taking the
        // presses meant for the chrome on top.
        mount(FormFactor.Tv)

        onNodeWithTag(TOUCH_CHROME_TAG).assertDoesNotExist()
        onNodeWithTag(DESKTOP_CHROME_TAG).assertDoesNotExist()
    }
}
