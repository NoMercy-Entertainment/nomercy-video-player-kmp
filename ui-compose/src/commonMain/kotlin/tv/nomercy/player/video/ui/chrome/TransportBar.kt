// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.ui.tv.PlayerIconButton
import tv.nomercy.player.video.ui.tv.PlayerIcons
import tv.nomercy.player.video.ui.tv.TvChromeStrings
import tv.nomercy.player.video.ui.tv.formatTime

// The transport row, for a pointer or a finger rather than a remote.
//
// The same state and the same commands the television chrome uses; what differs
// is the arrangement and that every button here is reachable directly rather
// than by moving a highlight. Sharing the model is what keeps a pause button
// meaning the same thing on a phone and on a television.
//
// Which buttons appear is the host's to decide, and the default is transport
// only. A bar that shipped with everything enabled would offer controls a build
// may not support, and a control that does nothing is worse than one that is
// absent.
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
        if (buttons.time) {
            BasicText(text = formatTime(state.timeSeconds), style = TextStyle(color = Color.White))
        }

        TransportButtons(state, commands, strings, buttons)

        MenuButtons(state, commands, strings, buttons)

        if (buttons.time) {
            // Remaining rather than total. Somebody deciding whether to start
            // another episode is asking how much is left.
            BasicText(
                text = "-" + formatTime((state.durationSeconds - state.timeSeconds).coerceAtLeast(0.0)),
                style = TextStyle(color = Color.White),
            )
        }
    }
}

@Composable
private fun TransportButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    // Gated on there being somewhere to go. A previous button on the first item
    // is a control a viewer presses to find out it does nothing.
    if (buttons.previousNext && state.hasPrevious) {
        PlayerIconButton(
            icon = PlayerIcons.Restart,
            description = strings.restart,
            onClick = { commands.seekTo(0.0) },
        )
    }

    if (buttons.playPause) {
        // The glyph and the label are one decision rather than two conditions
        // that happen to read the same state. Written apart, an edit to one is
        // an edit to half of it — a pause glyph announcing itself as Play, which
        // is invisible to anyone looking at the screen and wrong for everyone
        // who is not.
        val control: TransportControl = if (state.playing) {
            TransportControl(PlayerIcons.Pause, strings.pause)
        } else {
            TransportControl(PlayerIcons.Play, strings.play)
        }

        PlayerIconButton(
            icon = control.icon,
            description = control.description,
            onClick = { commands.setPlaying(!state.playing) },
            modifier = Modifier.testTag(PLAY_PAUSE_TAG),
        )
    }

    if (buttons.previousNext && state.hasNext) {
        PlayerIconButton(
            icon = PlayerIcons.Next,
            description = strings.next,
            onClick = { commands.next() },
        )
    }
}

@Composable
private fun MenuButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    buttons: ChromeButtons,
) {
    // Offered only where there is a choice. One audio track is not a menu, it is
    // a row that opens onto itself.
    if (buttons.audio && state.audioTracks.size > 1) {
        PlayerIconButton(
            icon = PlayerIcons.Episodes,
            description = strings.language,
            onClick = { commands.openAudioMenu() },
        )
    }

    if (buttons.subtitles) {
        PlayerIconButton(
            icon = PlayerIcons.Subtitles,
            description = strings.subtitles,
            onClick = { commands.openSubtitleMenu() },
        )
    }
}

// A glyph and what it announces itself as, which are the same choice.
private data class TransportControl(val icon: ImageVector, val description: String)

internal const val TRANSPORT_BAR_TAG = "nm-transport-bar"
internal const val PLAY_PAUSE_TAG = "nm-play-pause"

private val ROW_PADDING = 16.dp
private val GAP = 8.dp
