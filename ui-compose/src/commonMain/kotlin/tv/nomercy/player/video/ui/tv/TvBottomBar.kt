// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

// The transport row along the bottom.
//
// Given state and callbacks rather than a store. What it drew before reached a
// playback store directly, which is why it could not be rendered anywhere except
// inside the application that owned one.
@Composable
public fun TvBottomBar(
    state: TvTransportState,
    callbacks: TvChromeCallbacks,
    strings: TvChromeStrings,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(BAR_PADDING).testTag(BOTTOM_BAR_TAG)) {
        ChapterProgressBar(
            timeSeconds = state.timeSeconds,
            durationSeconds = state.durationSeconds,
            chapters = state.chapters,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = ROW_GAP),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        ) {
            BasicText(text = formatTime(state.timeSeconds), style = TextStyle(color = Color.White))

            PlayerIconButton(
                icon = PlayerIcons.Restart,
                description = strings.restart,
                onClick = callbacks::restart,
            )

            // The one control a viewer reaches for without looking, so it is the
            // one that takes focus when the bar appears.
            PlayerIconButton(
                icon = if (state.isPlaying) PlayerIcons.Pause else PlayerIcons.Play,
                description = if (state.isPlaying) strings.pause else strings.play,
                onClick = callbacks::togglePlay,
                focusRequester = playFocusRequester,
            )

            PlayerIconButton(
                icon = PlayerIcons.Next,
                description = strings.next,
                onClick = callbacks::next,
            )

            // Remaining rather than total. Somebody deciding whether to start
            // another episode is asking how much is left, not how long it was.
            BasicText(
                text = "-" + formatTime((state.durationSeconds - state.timeSeconds).coerceAtLeast(0.0)),
                style = TextStyle(color = Color.White),
            )
        }
    }
}

// Every string the chrome puts on screen, supplied by the host.
//
// A library that shipped English would be a library nobody outside English can
// use, and one that reached for a resource identifier would only work on
// Android.
public data class TvChromeStrings(
    val play: String = "Play",
    val pause: String = "Pause",
    val next: String = "Next",
    val restart: String = "Restart",
    val subtitles: String = "Subtitles",
    val episodes: String = "Episodes",
    val loading: String = "Loading",
    val resume: String = "Resume",
    val language: String = "Language",
    val searchSubtitles: String = "Search online",
    // Appended rather than placed beside the other transport labels, because a
    // data class is positional to anyone who constructed one that way and
    // inserting a field in the middle would silently move their arguments along.
    val close: String = "Close",
)

internal const val BOTTOM_BAR_TAG = "tv-bottom-bar"

private val BAR_PADDING = 24.dp
private val ROW_GAP = 12.dp
private val BUTTON_GAP = 8.dp
