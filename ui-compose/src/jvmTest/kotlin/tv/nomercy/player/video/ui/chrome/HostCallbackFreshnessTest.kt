// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.chrome.menus.MenuState
import tv.nomercy.player.video.ui.tv.TvChrome
import tv.nomercy.player.video.ui.tv.rememberTvChrome

// A host's lambda changes when the state it closes over changes. The chrome has
// to call the lambda it was given last, not the one it was first composed with.
@OptIn(ExperimentalTestApi::class)
class HostCallbackFreshnessTest {

    @Test
    fun theTvChromeCallsTheLatestEpisodeAndExitCallbacks() = runComposeUiTest {
        val player = NMVideoPlayer(RecordingVideoBackend())
        val calls = mutableListOf<String>()
        var onSelect: (String) -> Unit by mutableStateOf({ calls += "first-select:$it" })
        var onExit: () -> Unit by mutableStateOf({ calls += "first-exit" })
        var chrome: TvChrome? = null

        setContent { chrome = rememberTvChrome(player, onExit = onExit, onSelectEpisode = onSelect) }
        waitForIdle()

        runOnIdle {
            onSelect = { calls += "second-select:$it" }
            onExit = { calls += "second-exit" }
        }
        waitForIdle()

        runOnIdle {
            chrome!!.controller.chooseEpisode("e1")
            chrome!!.controller.onKey(PlayerKey.Stop)
        }

        assertEquals(listOf("second-select:e1", "second-exit"), calls)
    }

    @Test
    fun theVideoCommandsCallTheLatestPreferenceAndMenuCallbacks() = runComposeUiTest {
        val player = NMVideoPlayer(RecordingVideoBackend())
        val calls = mutableListOf<String>()
        var onAutoSkip: (Boolean) -> Unit by mutableStateOf({ calls += "first-auto:$it" })
        var onClock: (Boolean) -> Unit by mutableStateOf({ calls += "first-clock:$it" })
        var onMenu: (MenuState) -> Unit by mutableStateOf({ calls += "first-menu:$it" })
        var commands: ChromeCommands? = null

        setContent {
            commands = rememberVideoCommands(
                player,
                AutoSkipPreference(enabled = false, onChange = onAutoSkip),
                ClockPreference(showRemaining = true, onChange = onClock),
                onMenu,
            )
        }
        waitForIdle()

        runOnIdle {
            onAutoSkip = { calls += "second-auto:$it" }
            onClock = { calls += "second-clock:$it" }
            onMenu = { calls += "second-menu:$it" }
        }
        waitForIdle()

        runOnIdle {
            commands!!.setAutoSkipChapters(true)
            commands!!.setShowRemaining(false)
            commands!!.openAudioMenu()
        }

        assertEquals(
            listOf("second-auto:true", "second-clock:false", "second-menu:${MenuState.Audio}"),
            calls,
        )
    }
}
