// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.junit.Rule
import org.junit.Test
import tv.nomercy.player.core.input.PlayerKey
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tv.nomercy.player.video.tv.Cancellable
import tv.nomercy.player.video.tv.TvChromeCallbacks
import tv.nomercy.player.video.tv.TvChromeContent
import tv.nomercy.player.video.tv.TvChromeController
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.tv.TvDialog
import tv.nomercy.player.video.tv.TvEpisode
import tv.nomercy.player.video.tv.TvTransportState

// The acceptance gate: the whole chrome, driven by a directional pad, on an
// actual television.
//
// Everything below this has been tested a piece at a time. What this asks is
// whether the pieces are wired to each other — whether a press on the root
// reaches the state machine, whether the state machine's answer reaches the
// widgets, and whether the presses it does not want are handed back.
class NMTvPlayerViewTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recording : TvChromeCallbacks {
        val calls: MutableList<String> = mutableListOf()
        override fun play() { calls += "play" }
        override fun pause() { calls += PAUSED }
        override fun togglePlay() { calls += "togglePlay" }
        override fun seek(seconds: Float) = Unit
        override fun overrideTime(seconds: Float?) = Unit
        override fun restart() = Unit
        override fun next() = Unit
        override fun exitPlayer() { calls += "exitPlayer" }
    }

    private val callbacks = Recording()
    private val unhandled: MutableList<PlayerKey> = mutableListOf()

    private fun mount(
        playing: Boolean = true,
        onPreScreen: Boolean = false,
        content: TvChromeContent = TvChromeContent(item = TvChromeItem(title = FILM)),
    ): TvChromeController {
        // A real scheduler, because this is asking whether the wiring works
        // rather than what happens after five seconds.
        val controller = TvChromeController(
            callbacks = callbacks,
            scheduler = { _, _ -> Cancellable { } },
            playing = playing,
            startOnPreScreen = onPreScreen,
        )

        compose.setContent {
            NMTvPlayerView(
                controller = controller,
                transport = TvTransportState(isPlaying = playing, timeSeconds = 60.0, durationSeconds = WHOLE_FILM),
                content = content,
                strings = TvChromeStrings(),
                onUnhandledKey = { unhandled += it; false },
            )
        }
        return controller
    }

    private fun press(key: Key) {
        compose.onNodeWithTag(ROOT_TAG).requestFocus()
        compose.onNodeWithTag(ROOT_TAG).performKeyInput { pressKey(key) }
    }

    @Test
    fun aSidewaysPressStartsScrubbingAndStopsTheFilm() {
        mount(playing = true)

        press(Key.DirectionLeft)

        assertTrue(callbacks.calls.contains(PAUSED))
        compose.onNodeWithTag(SEEK_TAG).assertIsDisplayed()
    }

    @Test
    fun aVerticalPressBringsTheControlsUp() {
        mount(playing = true)

        press(Key.DirectionUp)

        compose.onNodeWithTag(BOTTOM_BAR_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TOP_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun theCentreButtonTogglesWhileNothingIsOnScreen() {
        mount(playing = false)

        press(Key.DirectionCenter)

        assertEquals(listOf("togglePlay"), callbacks.calls)
    }

    @Test
    fun aPressTheChromeDoesNotWantIsHandedBack() {
        // The failure the implementation this replaces had: it consumed presses
        // it had no use for, and a television stopped responding to its own
        // remote.
        mount(playing = true)

        press(Key.MediaPlayPause)

        assertEquals(listOf(PlayerKey.MediaPlayPause), unhandled)
    }

    @Test
    fun backFromWatchingBringsUpThePreScreenRatherThanLeaving() {
        mount(playing = true)

        press(Key.Back)

        compose.onNodeWithTag(PRE_SCREEN_TAG).assertIsDisplayed()
        assertTrue(callbacks.calls.contains(PAUSED))
    }

    @Test
    fun theControlsAndTheScrubberAreNeverBothUp() {
        // Two bars over one picture, one of them stale. It is the combination
        // holding the state in one value exists to make impossible.
        mount(playing = true)

        press(Key.DirectionUp)
        press(Key.DirectionLeft)

        compose.onNodeWithTag(SEEK_TAG).assertIsDisplayed()
        compose.onNodeWithTag(BOTTOM_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun openingAListTakesTheChromeOffTheScreen() {
        val controller: TvChromeController = mount(
            playing = true,
            content = TvChromeContent(
                item = TvChromeItem(show = FILM),
                episodes = listOf(TvEpisode("e1", "One"), TvEpisode("e2", "Two", isCurrent = true)),
            ),
        )

        compose.runOnUiThread { controller.openDialog(TvDialog.Episodes) }

        compose.onNodeWithTag(EPISODES_TAG).assertIsDisplayed()
        compose.onNodeWithTag(BOTTOM_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun aListSwallowsThePressesMeantForIt() {
        // The bug the implementation this replaces had: it checked one dialog
        // and forgot the others, so a press meant for a list moved the film
        // behind it.
        val controller: TvChromeController = mount(playing = true)

        compose.runOnUiThread { controller.openDialog(TvDialog.Language) }
        press(Key.DirectionLeft)

        compose.onNodeWithTag(SEEK_TAG).assertDoesNotExist()
    }

    @Test
    fun thePreScreenIsWhereAViewerStarts() {
        mount(playing = false, onPreScreen = true)

        compose.onNodeWithTag(PRE_SCREEN_TAG).assertIsDisplayed()
        compose.onNodeWithTag(BOTTOM_BAR_TAG).assertDoesNotExist()
    }
}

private const val FILM = "Rail Wars"
private const val PAUSED = "pause"
private const val WHOLE_FILM = 600.0
