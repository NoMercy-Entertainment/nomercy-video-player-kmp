// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.tv.formatTime
import tv.nomercy.player.video.tv.nextChapterStart
import tv.nomercy.player.video.tv.previousChapterStart
import tv.nomercy.player.video.ui.tv.FluentIcons
import tv.nomercy.player.video.ui.tv.PlayerIconButton
import tv.nomercy.player.video.ui.tv.TvChromeStrings

// The transport row, for a pointer or a finger rather than a remote.
//
// The order below is the web player's, control for control, from
// desktop-ui/helpers/dom.ts buildBottomRow. It is not an arrangement chosen
// here: a chrome with the same controls in a different order is a different
// player to anyone who has used the web one. This row was five controls in an
// order of its own until it was graded against that file, and two of the five
// were not on the web bar at all.
//
// The glyphs are the web's too. FluentIcons carries the same path data the
// browser renders, generated from the same table by
// scripts/generate-player-icons.py rather than redrawn.
@Composable
public fun TransportBar(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    modifier: Modifier = Modifier,
    buttons: ChromeButtons = ChromeButtons(),
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(ROW_PADDING).testTag(TRANSPORT_BAR_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        TransportButtons(state, commands, strings, buttons)
        VolumeCluster(state, commands, strings, buttons)
        TimeReadout(state, buttons)
        ViewButtons(state, commands, strings, buttons)
        MenuButtons(state, commands, strings, buttons)
    }
}

// Web order 1-7, in three groups because the web bar has three: the play
// button, the two seek steps, and the two chapter jumps. Split along those
// seams rather than at an arbitrary line count, so a control moves with the
// group it belongs to.
@Composable
private fun TransportButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    PlayPauseAndPrevious(state, commands, strings, buttons)
    SeekButtons(commands, strings, buttons)
    ChapterButtons(state, commands, strings, buttons)
    NextButton(state, commands, strings, buttons)
}

// Next closes the transport group, after the chapter jumps.
@Composable
private fun NextButton(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    if (buttons.previousNext && state.hasNext) {
        PlayerIconButton(
            icon = FluentIcons.Next,
            description = strings.next,
            onClick = { commands.next() },
        )
    }
}

// Play leads the row, previous follows it.
@Composable
private fun PlayPauseAndPrevious(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    if (buttons.playPause) {
        // The glyph and the label are one decision rather than two conditions
        // that happen to read the same state. Written apart, an edit to one is
        // an edit to half of it — a pause glyph announcing itself as Play, which
        // is invisible to anyone looking at the screen and wrong for everyone
        // who is not.
        val control: TransportControl = if (state.playing) {
            TransportControl(FluentIcons.Pause, strings.pause)
        } else {
            TransportControl(FluentIcons.Play, strings.play)
        }

        PlayerIconButton(
            icon = control.icon,
            description = control.description,
            onClick = { commands.setPlaying(!state.playing) },
            modifier = Modifier.testTag(PLAY_PAUSE_TAG),
        )
    }

    // Gated on there being somewhere to go. A previous button on the first item
    // is a control a viewer presses to find out it does nothing.
    if (buttons.previousNext && state.hasPrevious) {
        PlayerIconButton(
            icon = FluentIcons.Previous,
            description = strings.previous,
            onClick = { commands.previous() },
        )
    }

}

// The two ten-second steps.
@Composable
private fun SeekButtons(
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    if (buttons.seekBack) {
        PlayerIconButton(
            icon = FluentIcons.SeekBack,
            description = strings.seekBack,
            onClick = { commands.seekBy(-SEEK_STEP_SECONDS) },
            modifier = Modifier.testTag(SEEK_BACK_TAG),
        )
    }

    if (buttons.seekForward) {
        PlayerIconButton(
            icon = FluentIcons.SeekForward,
            description = strings.seekForward,
            onClick = { commands.seekBy(SEEK_STEP_SECONDS) },
            modifier = Modifier.testTag(SEEK_FORWARD_TAG),
        )
    }

}

// Only where the item has chapters. An item with none is not a player missing a
// feature, and two dead buttons say otherwise.
@Composable
private fun ChapterButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    if (buttons.chapters && state.chapters.isNotEmpty()) {
        val starts: List<Double> = state.chapters.map { it.startSeconds }

        PlayerIconButton(
            icon = FluentIcons.ChapterBack,
            description = strings.chapterBack,
            onClick = { commands.seekTo(previousChapterStart(starts, state.timeSeconds)) },
            modifier = Modifier.testTag(CHAPTER_BACK_TAG),
        )

        PlayerIconButton(
            icon = FluentIcons.ChapterForward,
            description = strings.chapterForward,
            onClick = { nextChapterStart(starts, state.timeSeconds)?.let(commands::seekTo) },
            modifier = Modifier.testTag(CHAPTER_FORWARD_TAG),
        )
    }

}

// Web order 8. The glyph is the level rather than one speaker: the web has
// volumeHigh, volumeMedium, volumeLow and volumeMuted and picks between them,
// so a viewer reads the level without opening anything.
@Composable
private fun VolumeCluster(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    if (!buttons.volume) return

    val icon: ImageVector = when {
        state.muted || state.volume == 0 -> FluentIcons.VolumeMuted
        state.volume < VOLUME_LOW -> FluentIcons.VolumeLow
        state.volume < VOLUME_HIGH -> FluentIcons.VolumeMedium
        else -> FluentIcons.VolumeHigh
    }

    PlayerIconButton(
        icon = icon,
        description = if (state.muted) strings.unmute else strings.mute,
        onClick = { commands.setMuted(!state.muted) },
        modifier = Modifier.testTag(VOLUME_TAG),
    )
}

// Web order 9-11: current time, a divider element, remaining time.
@Composable
private fun TimeReadout(state: ChromeState, buttons: ChromeButtons) {
    if (!buttons.time) return

    BasicText(text = formatTime(state.timeSeconds), style = READOUT)

    // A real element on the web rather than padding, so it is one here too.
    Box(modifier = Modifier.size(width = DIVIDER_WIDTH, height = DIVIDER_HEIGHT))

    // Remaining rather than total. Somebody deciding whether to start another
    // episode is asking how much is left.
    BasicText(
        text = "-" + formatTime((state.durationSeconds - state.timeSeconds).coerceAtLeast(0.0)),
        style = READOUT,
    )
}

// Web order 12-15: aspect ratio, theater, picture in picture, speed.
@Composable
private fun ViewButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    if (buttons.aspectRatio) {
        PlayerIconButton(
            icon = FluentIcons.AspectFit,
            description = strings.aspectRatio,
            onClick = { commands.cycleAspectRatio() },
            modifier = Modifier.testTag(ASPECT_RATIO_TAG),
        )
    }

    if (buttons.theater) {
        PlayerIconButton(
            icon = if (state.theater) FluentIcons.TheaterExit else FluentIcons.Theater,
            description = strings.theater,
            onClick = { commands.setTheater(!state.theater) },
            modifier = Modifier.testTag(THEATER_TAG),
        )
    }

    if (buttons.pictureInPicture) {
        PlayerIconButton(
            icon = if (state.pip) FluentIcons.PipExit else FluentIcons.PipEnter,
            description = strings.pictureInPicture,
            onClick = { commands.setPip(!state.pip) },
            modifier = Modifier.testTag(PIP_TAG),
        )
    }

    if (buttons.speed) {
        PlayerIconButton(
            icon = FluentIcons.Speed,
            description = strings.speed,
            onClick = { commands.openSpeedMenu() },
            modifier = Modifier.testTag(SPEED_TAG),
        )
    }
}

// Web order 16-20: subtitles, audio, quality, playlist, settings.
@Composable
private fun MenuButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    if (buttons.subtitles) {
        // subtitlesOff when none is on, which is how the web says the difference
        // without a viewer opening the menu to find out.
        PlayerIconButton(
            icon = if (state.activeSubtitle == null) FluentIcons.SubtitlesOff else FluentIcons.Subtitles,
            description = strings.subtitles,
            onClick = { commands.openSubtitleMenu() },
        )
    }

    // Offered only where there is a choice. One audio track is not a menu, it is
    // a row that opens onto itself.
    if (buttons.audio && state.audioTracks.size > 1) {
        PlayerIconButton(
            icon = FluentIcons.Language,
            description = strings.language,
            onClick = { commands.openAudioMenu() },
        )
    }

    ListMenuButtons(state, commands, strings, buttons)
}

// Quality, playlist and settings — the three that open a list rather than pick
// a track.
@Composable
private fun ListMenuButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    if (buttons.quality && state.qualityLevels.isNotEmpty()) {
        PlayerIconButton(
            icon = FluentIcons.Quality,
            description = strings.quality,
            onClick = { commands.openQualityMenu() },
            modifier = Modifier.testTag(QUALITY_TAG),
        )
    }

    if (buttons.playlist && state.queueSize > 1) {
        PlayerIconButton(
            icon = FluentIcons.Playlist,
            description = strings.playlist,
            onClick = { commands.openPlaylistMenu() },
            modifier = Modifier.testTag(PLAYLIST_TAG),
        )
    }

    if (buttons.settings) {
        PlayerIconButton(
            icon = FluentIcons.Settings,
            description = strings.settings,
            onClick = { commands.openSettingsMenu() },
            modifier = Modifier.testTag(SETTINGS_TAG),
        )
    }
}

// A glyph and what it announces itself as, which are the same choice.
private data class TransportControl(val icon: ImageVector, val description: String)

internal const val TRANSPORT_BAR_TAG = "nm-transport-bar"
internal const val PLAY_PAUSE_TAG = "nm-play-pause"
internal const val SEEK_BACK_TAG = "nm-seek-back"
internal const val SEEK_FORWARD_TAG = "nm-seek-forward"
internal const val CHAPTER_BACK_TAG = "nm-chapter-back"
internal const val CHAPTER_FORWARD_TAG = "nm-chapter-forward"
internal const val VOLUME_TAG = "nm-volume"
internal const val ASPECT_RATIO_TAG = "nm-aspect-ratio"
internal const val THEATER_TAG = "nm-theater"
internal const val PIP_TAG = "nm-pip"
internal const val SPEED_TAG = "nm-speed"
internal const val QUALITY_TAG = "nm-quality"
internal const val PLAYLIST_TAG = "nm-playlist"
internal const val SETTINGS_TAG = "nm-settings"

private val READOUT = TextStyle(color = Color.White)

private val ROW_PADDING = 16.dp
private val GAP = 8.dp
private val DIVIDER_WIDTH = 1.dp
private val DIVIDER_HEIGHT = 12.dp

// The web's own step, and the number its tooltip says out loud.
private const val SEEK_STEP_SECONDS = 10f

private const val VOLUME_LOW = 33
private const val VOLUME_HIGH = 66
