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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.tv.TvDialog
import tv.nomercy.player.video.tv.episodeLabel
import tv.nomercy.player.video.tv.showTitle

// What is playing, and the two lists a viewer opens from here.
//
// The title logic is not in this file. It is a pure function with its own tests,
// because the branching behind an episode label is where an extraction goes
// wrong quietly and a widget is the worst place to find that out.
@Composable
public fun TvTopBar(
    item: TvChromeItem?,
    strings: TvChromeStrings,
    onOpen: (TvDialog) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(BAR_PADDING).testTag(TOP_BAR_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            BasicText(
                text = showTitle(item, strings.loading),
                style = TextStyle(color = Color.White, fontSize = TITLE_SIZE),
            )

            // Only where there is one. An empty line still takes its height, so a
            // film would carry a gap under its own name for no reason.
            val subtitle: String = episodeLabel(item)
            if (subtitle.isNotEmpty()) {
                BasicText(text = subtitle, style = TextStyle(color = Color.White, fontSize = SUBTITLE_SIZE))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            PlayerIconButton(
                icon = FluentIcons.Playlist,
                description = strings.episodes,
                onClick = { onOpen(TvDialog.Episodes) },
                onFocused = onFocusChanged,
            )
            PlayerIconButton(
                icon = FluentIcons.Subtitles,
                description = strings.subtitles,
                onClick = { onOpen(TvDialog.Subtitle) },
                onFocused = onFocusChanged,
            )
        }
    }
}

internal const val TOP_BAR_TAG = "tv-top-bar"

private val BAR_PADDING = 24.dp
private val BUTTON_GAP = 8.dp
private val TITLE_SIZE = 28.sp
private val SUBTITLE_SIZE = 18.sp
