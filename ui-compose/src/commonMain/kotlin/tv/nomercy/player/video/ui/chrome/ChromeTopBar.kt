// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.nomercy.player.video.tv.EpisodeLabels
import tv.nomercy.player.video.ui.tv.PlayerIconButton
import tv.nomercy.player.video.ui.tv.PlayerIcons
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.ui.tv.TvChromeStrings
import tv.nomercy.player.video.tv.episodeLabel
import tv.nomercy.player.video.tv.showTitle

// The name across the top, and the way out.
//
// The same two lines the television bar draws, from the same two functions, so a
// viewer moving between a phone and a set-top box reads the same title in the
// same shape. What differs is only that this one has somewhere to press: a
// television leaves by the remote's back button and a phone has nothing else.
//
// Leaving is a callback rather than something this does. Where "out" goes is the
// host's — a route, a dismissed sheet, a closed window — and a library that
// picked one would be wrong in every app that picked another.
@Composable
public fun ChromeTopBar(
    item: TvChromeItem?,
    strings: TvChromeStrings,
    modifier: Modifier = Modifier,
    labels: EpisodeLabels = EpisodeLabels(),
    onClose: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val episode: String = episodeLabel(item, labels)

    Row(
        modifier = modifier.fillMaxWidth().padding(BAR_PADDING).testTag(TOP_BAR_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        if (onClose != null) {
            PlayerIconButton(
                icon = PlayerIcons.Close,
                description = strings.close,
                onClick = onClose,
                modifier = Modifier.testTag(CLOSE_TAG),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = showTitle(item, strings.loading),
                style = TextStyle(color = Color.White, fontSize = TITLE_SIZE, fontWeight = FontWeight.Bold),
            )

            // Absent rather than blank on a film, whose name is already on the
            // line above. An empty second line is a gap a viewer reads as
            // something that failed to load.
            if (episode.isNotEmpty()) {
                BasicText(
                    text = episode,
                    style = TextStyle(color = Color.White, fontSize = EPISODE_SIZE),
                    modifier = Modifier.testTag(EPISODE_TAG),
                )
            }
        }

        // Where a host puts what only it has: a cast button, a chapter list, a
        // share sheet. Empty by default, because a slot the library filled would
        // be one every consumer has to undo.
        trailing()
    }
}

internal const val TOP_BAR_TAG = "nm-chrome-top-bar"
internal const val CLOSE_TAG = "nm-chrome-close"
internal const val EPISODE_TAG = "nm-chrome-episode"

private val BAR_PADDING = 16.dp
private val GAP = 12.dp
private val TITLE_SIZE = 18.sp
private val EPISODE_SIZE = 14.sp
