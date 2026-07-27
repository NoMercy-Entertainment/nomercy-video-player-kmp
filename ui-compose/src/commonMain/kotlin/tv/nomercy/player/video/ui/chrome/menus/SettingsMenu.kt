// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.ui.chrome.ChromeCommands
import tv.nomercy.player.video.ui.chrome.ChromeState

// Which list is open, if any.
//
// One value rather than a boolean per menu. Two open at once is not a state
// anybody designed; it is what happens when five flags are set independently,
// and it is how a viewer ends up choosing a quality from behind a subtitle list.
public enum class MenuState { Hidden, Main, Quality, Audio, Subtitle, Speed }

// The settings surface: a main list that opens the others.
//
// Every row keys by the descriptor it selects rather than by its position. A
// track list changes when a stream switches rendition, and an index into a list
// that has since changed selects whatever moved into that slot — which is the
// wrong audio language, silently, on exactly the streams that adapt most.
@Composable
public fun SettingsMenu(
    state: ChromeState,
    commands: ChromeCommands,
    menu: MenuState,
    onMenuChange: (MenuState) -> Unit,
    modifier: Modifier = Modifier,
    strings: MenuStrings = MenuStrings(),
) {
    if (menu == MenuState.Hidden) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SCRIM)
            .padding(MENU_PADDING)
            .testTag(SETTINGS_MENU_TAG),
    ) {
        when (menu) {
            MenuState.Main -> MainMenu(state, strings, onMenuChange)
            MenuState.Quality -> QualityMenu(state, commands, strings, onMenuChange)
            MenuState.Audio -> AudioMenu(state, commands, onMenuChange)
            MenuState.Subtitle -> SubtitleMenu(state, commands, strings, onMenuChange)
            MenuState.Speed -> SpeedMenu(state, commands, strings, onMenuChange)
            MenuState.Hidden -> Unit
        }
    }
}

// Only the lists that have something in them. A row that opens onto one option
// is a press that costs a viewer time and gives them no choice.
@Composable
private fun MainMenu(state: ChromeState, strings: MenuStrings, onMenuChange: (MenuState) -> Unit) {
    Column {
        if (state.qualityLevels.size > 1) {
            MenuRow(strings.quality, tag = ROW_QUALITY) { onMenuChange(MenuState.Quality) }
        }
        if (state.audioTracks.size > 1) {
            MenuRow(strings.audio, tag = ROW_AUDIO) { onMenuChange(MenuState.Audio) }
        }
        // Offered whenever the feature is on, even with nothing loaded: turning
        // subtitles off is a choice, and so is finding out there are none.
        MenuRow(strings.subtitles, tag = ROW_SUBTITLE) { onMenuChange(MenuState.Subtitle) }
        MenuRow(strings.speed, tag = ROW_SPEED) { onMenuChange(MenuState.Speed) }
    }
}

@Composable
private fun QualityMenu(
    state: ChromeState,
    commands: ChromeCommands,
    strings: MenuStrings,
    onMenuChange: (MenuState) -> Unit,
) {
    LazyColumn {
        // Automatic first, because it is what most viewers should stay on and
        // the list below it exists for the ones who know they want otherwise.
        item {
            MenuRow(strings.automatic, isCurrent = state.activeQuality == null, tag = ROW_AUTO) {
                commands.selectQuality(null)
                onMenuChange(MenuState.Hidden)
            }
        }

        items(state.qualityLevels) { level ->
            MenuRow(qualityLabel(level), isCurrent = level == state.activeQuality) {
                commands.selectQuality(level)
                onMenuChange(MenuState.Hidden)
            }
        }
    }
}

@Composable
private fun AudioMenu(
    state: ChromeState,
    commands: ChromeCommands,
    onMenuChange: (MenuState) -> Unit,
) {
    LazyColumn {
        items(state.audioTracks) { track ->
            MenuRow(audioLabel(track), isCurrent = track == state.activeAudio) {
                commands.selectAudioTrack(track)
                onMenuChange(MenuState.Hidden)
            }
        }
    }
}

@Composable
private fun SubtitleMenu(
    state: ChromeState,
    commands: ChromeCommands,
    strings: MenuStrings,
    onMenuChange: (MenuState) -> Unit,
) {
    LazyColumn {
        // Off is a row rather than an absence. A viewer turning subtitles off
        // has to be able to say so, and a list with no way back is one they
        // leave by restarting the film.
        item {
            MenuRow(strings.subtitlesOff, isCurrent = state.activeSubtitle == null, tag = ROW_SUBTITLE_OFF) {
                commands.selectSubtitleTrack(null)
                onMenuChange(MenuState.Hidden)
            }
        }

        items(state.subtitleTracks) { track ->
            MenuRow(track.label, isCurrent = track == state.activeSubtitle) {
                commands.selectSubtitleTrack(track)
                onMenuChange(MenuState.Hidden)
            }
        }
    }
}

@Composable
private fun SpeedMenu(
    state: ChromeState,
    commands: ChromeCommands,
    strings: MenuStrings,
    onMenuChange: (MenuState) -> Unit,
) {
    LazyColumn {
        items(SPEEDS) { speed ->
            MenuRow(speedLabel(speed, strings), isCurrent = speed == state.rate) {
                commands.setRate(speed)
                onMenuChange(MenuState.Hidden)
            }
        }
    }
}

// Height and dynamic range, because the same height in HDR is a different stream
// a device may not be able to play, and a list showing two identical rows is one
// a viewer cannot choose between.
internal fun qualityLabel(level: QualityLevel): String =
    "${level.height}p" + if (level.dynamicRange.wire == SDR_WIRE) "" else " ${level.dynamicRange.wire}"

// The label the stream gave, because "English" and "English (Commentary)" are
// different tracks somebody chooses between and the language alone hides that.
internal fun audioLabel(track: AudioTrack): String = track.label

internal fun speedLabel(speed: Float, strings: MenuStrings): String =
    if (speed == 1f) strings.normalSpeed else "${speed}x"

public data class MenuStrings(
    val quality: String = "Quality",
    val audio: String = "Audio",
    val subtitles: String = "Subtitles",
    val subtitlesOff: String = "Off",
    val speed: String = "Speed",
    val automatic: String = "Auto",
    val normalSpeed: String = "Normal",
)

// The rates every player offers. Written here rather than asked of the engine,
// because an engine reports what it can do and this is what a viewer should be
// offered: a list of thirty options is not a menu.
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private const val SDR_WIRE = "sdr"

internal const val SETTINGS_MENU_TAG = "nm-settings-menu"
internal const val ROW_QUALITY = "nm-row-quality"
internal const val ROW_AUDIO = "nm-row-audio"
internal const val ROW_SUBTITLE = "nm-row-subtitle"
internal const val ROW_SUBTITLE_OFF = "nm-row-subtitle-off"
internal const val ROW_SPEED = "nm-row-speed"
internal const val ROW_AUTO = "nm-row-auto"

private val SCRIM = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.9f)
private val MENU_PADDING = 16.dp
