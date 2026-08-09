// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import tv.nomercy.player.video.tv.TvChromeCallbacks
import tv.nomercy.player.video.tv.TvChromeContent
import tv.nomercy.player.video.tv.TvDialog
import tv.nomercy.player.video.tv.episodeLabel
import tv.nomercy.player.video.tv.showTitle

// What a viewer sees when they press back, and before they press play.
//
// It is a menu rather than a dialog: pressing back from watching lands here, so
// it is also the last thing between them and leaving. That is why resume takes
// focus — it is the one option somebody who pressed back by accident wants, and
// it should be one press away.
@Composable
public fun TvPreScreen(
    content: TvChromeContent,
    strings: TvChromeStrings,
    actions: TvPreScreenActions,
    modifier: Modifier = Modifier,
) {
    val resume: FocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { resume.requestFocus() }

    Column(
        modifier = modifier.fillMaxSize().background(SCRIM).padding(SCREEN_PADDING).testTag(PRE_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        BasicText(
            text = showTitle(content.item, strings.loading),
            style = TextStyle(color = Color.White, fontSize = TITLE_SIZE),
        )

        val subtitle: String = episodeLabel(content.item)
        if (subtitle.isNotEmpty()) {
            BasicText(text = subtitle, style = TextStyle(color = Color.LightGray, fontSize = SUBTITLE_SIZE))
        }

        TvMenuItem(label = strings.resume, focusRequester = resume, onSelect = actions.resume)

        TvMenuItem(label = strings.restart, onSelect = actions.restart)

        // Only where there is more than one. A film with a single entry showing
        // an episode list is a row that opens onto itself.
        if (content.episodes.size > 1) {
            TvMenuItem(label = strings.episodes, onSelect = { actions.open(TvDialog.Episodes) })
        }

        if (content.audioTracks.size > 1) {
            TvMenuItem(label = strings.language, onSelect = { actions.open(TvDialog.Language) })
        }

        // Offered even with nothing in it, because the list is where searching
        // online lives and a film with no subtitles is exactly when somebody
        // wants that.
        TvMenuItem(label = strings.subtitles, onSelect = { actions.open(TvDialog.Subtitle) })
    }
}

/**
 * What the start menu can do, as one value.
 *
 * Grouped rather than passed as three lambdas beside the content and the
 * strings, because they are one thing — the menu's verbs — and threading them
 * separately is what pushed this composable past a readable parameter list.
 *
 * All three go through the controller rather than to the engine. Starting
 * playback is only half of what Resume MEANS; the other half is this screen
 * getting out of the way, and a row wired straight to the engine did the first
 * and never the second.
 */
public class TvPreScreenActions(
    public val resume: () -> Unit,
    public val restart: () -> Unit,
    public val open: (TvDialog) -> Unit,
)

internal const val PRE_SCREEN_TAG = "tv-pre-screen"

private val SCRIM = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.9f)
private val SCREEN_PADDING = 48.dp
private val ROW_GAP = 4.dp
private val TITLE_SIZE = 34.sp
private val SUBTITLE_SIZE = 20.sp
