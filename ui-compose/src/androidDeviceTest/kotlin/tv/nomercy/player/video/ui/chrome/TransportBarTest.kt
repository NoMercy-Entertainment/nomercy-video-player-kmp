// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.ui.tv.TvChromeStrings
import kotlin.test.assertEquals

// The transport row for a pointer or a finger.
//
// The two things worth checking on a device are which glyph reached the screen
// and whether pressing it reached the player, because neither can be seen from
// reading the composable.
class TransportBarTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recording : ChromeCommands {
        val calls: MutableList<String> = mutableListOf()
        var lastPlaying: Boolean? = null

        override fun seekTo(seconds: Double) { calls += "seekTo" }

        override fun seekBy(deltaSeconds: Float) { calls += "seekBy" }

        override fun setPlaying(playing: Boolean) {
            calls += "setPlaying"
            lastPlaying = playing
        }

        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun openAudioMenu() { calls += "openAudioMenu" }
        override fun openSubtitleMenu() { calls += "openSubtitleMenu" }
        override fun setVolume(percent: Int) = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun selectQuality(level: QualityLevel?) = Unit
        override fun selectAudioTrack(track: AudioTrack) = Unit
        override fun selectSubtitleTrack(track: SubtitleTrack?) = Unit
        override fun setRate(rate: Float) = Unit
        override fun setFullscreen(fullscreen: Boolean) = Unit
        override fun dismissMessage() = Unit
    }

    private val commands = Recording()
    private val strings = TvChromeStrings()

    private fun render(state: ChromeState, buttons: ChromeButtons = ChromeButtons()) {
        compose.setContent { TransportBar(state, commands, strings, buttons = buttons) }
    }

    @Test
    fun aPausedFilmOffersPlay() {
        render(ChromeState(playing = false))

        compose.onNodeWithContentDescription(strings.play).assertIsDisplayed()
    }

    @Test
    fun aPlayingFilmOffersPause() {
        render(ChromeState(playing = true))

        compose.onNodeWithContentDescription(strings.pause).assertIsDisplayed()
    }

    @Test
    fun pressingItAsksForTheOppositeOfWhatIsOnScreen() {
        // Explicit rather than a toggle. The viewer pressed the button they could
        // see, and state that changed underneath must not invert what they meant.
        render(ChromeState(playing = false))

        compose.onNodeWithTag(PLAY_PAUSE_TAG).performClick()

        assertEquals(listOf("setPlaying"), commands.calls)
        assertEquals(true, commands.lastPlaying)
    }

    @Test
    fun aNextButtonAppearsOnlyWhenThereIsSomethingNext() {
        // A control a viewer presses to find out it does nothing is worse than
        // one that is absent.
        render(ChromeState(queueSize = 2, queueIndex = 1))

        compose.onNodeWithContentDescription(strings.next).assertDoesNotExist()
    }

    @Test
    fun andDoesWhenThereIs() {
        render(ChromeState(queueSize = 2, queueIndex = 0))

        compose.onNodeWithContentDescription(strings.next).assertIsDisplayed()
    }

    @Test
    fun everythingBeyondTransportIsOffUntilAHostAsksForIt() {
        // The opposite of the obvious default, deliberately. Shipping every
        // button enabled puts controls in front of consumers whose build may not
        // support them.
        render(ChromeState())

        compose.onNodeWithContentDescription(strings.subtitles).assertDoesNotExist()
    }

    @Test
    fun aHostThatAsksForThemGetsThem() {
        render(ChromeState(), ChromeButtons(subtitles = true))

        compose.onNodeWithContentDescription(strings.subtitles).assertIsDisplayed()
    }

    @Test
    fun anAudioMenuIsOfferedOnlyWhenThereIsAChoice() {
        // One track is not a menu, it is a row that opens onto itself.
        val one = listOf(AudioTrack(id = "en", language = "en", label = "English"))
        render(ChromeState(audioTracks = one), ChromeButtons(audio = true))

        compose.onNodeWithContentDescription(strings.language).assertDoesNotExist()
    }

    @Test
    fun withTwoTracksItIs() {
        val two = listOf(
            AudioTrack(id = "en", language = "en", label = "English"),
            AudioTrack(id = "nl", language = "nl", label = "Nederlands"),
        )
        render(ChromeState(audioTracks = two), ChromeButtons(audio = true))

        compose.onNodeWithContentDescription(strings.language).assertIsDisplayed()
    }
}
