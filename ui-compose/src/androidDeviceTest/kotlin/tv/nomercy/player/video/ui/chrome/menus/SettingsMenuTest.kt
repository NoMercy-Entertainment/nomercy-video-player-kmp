// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.DynamicRange
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.ui.chrome.ChromeCommands
import tv.nomercy.player.video.ui.chrome.ChromeState
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The settings lists, on a device.
//
// What matters is that a choice reaches the player as the thing chosen rather
// than as a position, and that the list closes behind it. A menu left open over
// a film somebody has just changed is one they dismiss by pressing back into
// something else.
class SettingsMenuTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recording : ChromeCommands {
        var lastQuality: QualityLevel? = null
        var qualityWasSet: Boolean = false
        var lastAudio: AudioTrack? = null
        var lastSubtitle: SubtitleTrack? = null
        var subtitleWasSet: Boolean = false
        var lastRate: Float? = null

        override fun seekTo(seconds: Double) = Unit
        override fun setPlaying(playing: Boolean) = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun openAudioMenu() = Unit
        override fun openSubtitleMenu() = Unit
        override fun setVolume(percent: Int) = Unit
        override fun setMuted(muted: Boolean) = Unit

        override fun selectQuality(level: QualityLevel?) {
            lastQuality = level
            qualityWasSet = true
        }

        override fun selectAudioTrack(track: AudioTrack) { lastAudio = track }

        override fun selectSubtitleTrack(track: SubtitleTrack?) {
            lastSubtitle = track
            subtitleWasSet = true
        }

        override fun setRate(rate: Float) { lastRate = rate }
        override fun setFullscreen(fullscreen: Boolean) = Unit
        override fun dismissMessage() = Unit
    }

    private val commands = Recording()
    private val strings = MenuStrings()
    private var menu: MenuState = MenuState.Main

    private val hd = QualityLevel(height = 1080, bitrate = 6_000_000, codec = "h264")
    private val sd = QualityLevel(height = 720, bitrate = 3_000_000, codec = "h264")
    private val english = AudioTrack(id = "en", language = "en", label = "English")
    private val commentary = AudioTrack(id = "en2", language = "en", label = "English (Commentary)")
    private val dutchSubs = SubtitleTrack(id = "nl", language = "nl", label = "Nederlands")

    private fun render(state: ChromeState, open: MenuState) {
        menu = open
        compose.setContent {
            SettingsMenu(state, commands, menu, onMenuChange = { menu = it }, strings = strings)
        }
    }

    @Test
    fun aHiddenMenuDrawsNothing() {
        render(ChromeState(), MenuState.Hidden)

        compose.onNodeWithTag(SETTINGS_MENU_TAG).assertDoesNotExist()
    }

    @Test
    fun aListWithOneOptionIsNotOffered() {
        // A row that opens onto one option costs a press and gives no choice.
        render(ChromeState(qualityLevels = listOf(hd)), MenuState.Main)

        compose.onNodeWithTag(ROW_QUALITY).assertDoesNotExist()
    }

    @Test
    fun aListWithAChoiceIs() {
        render(ChromeState(qualityLevels = listOf(hd, sd)), MenuState.Main)

        compose.onNodeWithTag(ROW_QUALITY).assertIsDisplayed()
    }

    @Test
    fun choosingAQualitySendsTheLevelRatherThanItsPosition() {
        // A track list changes when a stream switches rendition, and an index
        // into a changed list selects whatever moved into that slot.
        render(ChromeState(qualityLevels = listOf(hd, sd)), MenuState.Quality)

        compose.onNodeWithContentDescription("1080p").performClick()

        assertEquals(hd, commands.lastQuality)
    }

    @Test
    fun andClosesTheMenuBehindIt() {
        render(ChromeState(qualityLevels = listOf(hd, sd)), MenuState.Quality)

        compose.onNodeWithContentDescription("1080p").performClick()

        assertEquals(MenuState.Hidden, menu)
    }

    @Test
    fun automaticIsOfferedAndIsNotATrack() {
        // What most viewers should stay on. Sending null rather than a level is
        // what tells the engine to keep adapting.
        render(ChromeState(qualityLevels = listOf(hd, sd)), MenuState.Quality)

        compose.onNodeWithTag(ROW_AUTO).performClick()

        assertTrue(commands.qualityWasSet)
        assertNull(commands.lastQuality)
    }

    @Test
    fun turningSubtitlesOffIsARowRatherThanAnAbsence() {
        // A list with no way back is one a viewer leaves by restarting the film.
        render(ChromeState(subtitleTracks = listOf(dutchSubs)), MenuState.Subtitle)

        compose.onNodeWithTag(ROW_SUBTITLE_OFF).performClick()

        assertTrue(commands.subtitleWasSet)
        assertNull(commands.lastSubtitle)
    }

    @Test
    fun choosingASubtitleTrackSendsIt() {
        render(ChromeState(subtitleTracks = listOf(dutchSubs)), MenuState.Subtitle)

        compose.onNodeWithContentDescription("Nederlands").performClick()

        assertEquals(dutchSubs, commands.lastSubtitle)
    }

    @Test
    fun twoTracksInOneLanguageAreToldApartByTheirLabel() {
        // "English" and "English (Commentary)" are different tracks somebody
        // chooses between, and the language alone hides that.
        render(ChromeState(audioTracks = listOf(english, commentary)), MenuState.Audio)

        compose.onNodeWithContentDescription("English (Commentary)").performClick()

        assertEquals(commentary, commands.lastAudio)
    }

    @Test
    fun normalSpeedIsNamedRatherThanNumbered() {
        // "1.0x" is a setting; "Normal" is what a viewer is looking for when
        // they want to undo whatever they did.
        render(ChromeState(rate = 1.5f), MenuState.Speed)

        compose.onNodeWithContentDescription(strings.normalSpeed).performClick()

        assertEquals(1f, commands.lastRate)
    }

    @Test
    fun theCurrentChoiceIsMarkedRatherThanOnlyColoured() {
        render(ChromeState(rate = 1.5f), MenuState.Speed)

        compose.onNodeWithContentDescription("1.5x").assertIsDisplayed()
    }
}
