// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The lists a viewer opens from the chrome.
//
// One implementation with a different set of rows each time, because a
// television list is the same interaction in every case. What they had before
// was four files that had drifted into four focus behaviours.
@Composable
public fun TvEpisodesDialog(
    episodes: List<TvEpisode>,
    onSelect: (String) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
) {
    TvListDialog(
        // Opening on the episode being watched rather than at the top. In a
        // twenty-four episode season the one somebody wants is the one they are
        // on, and scrolling to it from the top is the whole interaction.
        shape = TvListShape(title, EPISODES_TAG, episodes.indexOfFirst { it.isCurrent }),
        modifier = modifier,
    ) { requestFocus ->
        items(episodes, key = { it.id }) { episode ->
            TvMenuItem(
                label = episode.title,
                detail = episode.subtitle,
                isCurrent = episode.isCurrent,
                focusRequester = requestFocus(episode.isCurrent),
                onSelect = { onSelect(episode.id) },
            )
        }
    }
}

@Composable
public fun TvTrackDialog(
    tracks: List<TvTrack>,
    onSelect: (String) -> Unit,
    title: String,
    tag: String,
    modifier: Modifier = Modifier,
    extraRow: (@Composable () -> Unit)? = null,
) {
    TvListDialog(
        shape = TvListShape(title, tag, tracks.indexOfFirst { it.isCurrent }),
        modifier = modifier,
        footer = extraRow,
    ) { requestFocus ->
        items(tracks, key = { it.id }) { track ->
            TvMenuItem(
                label = track.label,
                detail = track.language,
                isCurrent = track.isCurrent,
                focusRequester = requestFocus(track.isCurrent),
                onSelect = { onSelect(track.id) },
            )
        }
    }
}

// The shape all of them share.
//
// Focus lands on the current row when the list opens. Without that a remote
// starts at the top of a list whose interesting entry is halfway down, and every
// viewer pays for it on every open.
@Composable
private fun TvListDialog(
    shape: TvListShape,
    modifier: Modifier,
    footer: (@Composable () -> Unit)? = null,
    rows: androidx.compose.foundation.lazy.LazyListScope.((Boolean) -> FocusRequester?) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentRow: FocusRequester = remember { FocusRequester() }

    LaunchedEffect(shape.startAt) {
        if (shape.startAt >= 0) {
            listState.scrollToItem(shape.startAt)
            currentRow.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SCRIM)
            .padding(DIALOG_PADDING)
            .testTag(shape.tag),
    ) {
        BasicText(text = shape.title, style = TextStyle(color = Color.White, fontSize = TITLE_SIZE))

        LazyColumn(state = listState) {
            rows { isCurrent -> if (isCurrent) currentRow else null }
        }

        footer?.invoke()
    }
}

// What a list is, apart from its rows. Three strings and a number in a row is
// where a caller swaps the title with the tag and nothing complains.
private data class TvListShape(val title: String, val tag: String, val startAt: Int)

internal const val EPISODES_TAG = "tv-episodes-dialog"
internal const val AUDIO_TAG = "tv-audio-dialog"
internal const val SUBTITLE_TAG = "tv-subtitle-dialog"

// Nearly opaque. A list over a moving picture is unreadable, and a viewer
// choosing a subtitle track is reading rather than watching.
private val SCRIM = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.9f)

private val DIALOG_PADDING = 32.dp
private val TITLE_SIZE = 24.sp
