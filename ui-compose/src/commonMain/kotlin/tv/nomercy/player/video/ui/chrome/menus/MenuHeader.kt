// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import tv.nomercy.player.video.ui.tv.FluentIcons
import tv.nomercy.player.video.ui.tv.PlayerIconButton

// The pane's name, a hairline, and the way out.
//
// Back where there is somewhere to go back to, close where there is not: the main
// list's cross dismisses the menu and a pane's arrow returns to the main list,
// which is the web's behaviour and the reason a viewer can get out of the
// subtitle settings without losing the settings menu.
@Composable
internal fun MenuHeader(strings: MenuStrings, menu: MenuState, onMenuChange: (MenuState) -> Unit) {
    val title: String = strings.titleFor(menu)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HEADER_MIN_HEIGHT)
            .padding(HEADER_PADDING)
            .testTag(MENU_HEADER_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        if (menu != MenuState.Main) {
            PlayerIconButton(
                icon = FluentIcons.Back,
                description = strings.back,
                onClick = { onMenuChange(MenuState.Main) },
                buttonSize = HEADER_BUTTON,
                iconSize = HEADER_ICON,
                modifier = Modifier.testTag(MENU_BACK_TAG),
            )
        }

        BasicText(
            text = title,
            style = TextStyle(color = Color.White, fontSize = HEADER_SIZE, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )

        PlayerIconButton(
            icon = FluentIcons.Close,
            description = strings.close,
            onClick = { onMenuChange(MenuState.Hidden) },
            buttonSize = HEADER_BUTTON,
            iconSize = HEADER_ICON,
            modifier = Modifier.testTag(MENU_CLOSE_TAG),
        )
    }

    // `border-bottom: 1px solid rgba(209, 213, 219, 0.2)`.
    Box(modifier = Modifier.fillMaxWidth().height(HEADER_RULE).background(HEADER_RULE_COLOR))
}

// Which pane a viewer is looking at. The web writes the name into the header, and
// a panel whose header always said "Settings" would be lying three panes deep.
private fun MenuStrings.titleFor(menu: MenuState): String = when (menu) {
    MenuState.Quality -> quality
    MenuState.Audio -> audio
    MenuState.Subtitle -> subtitles
    MenuState.Speed -> speed
    MenuState.Playlist -> playlist
    MenuState.AspectRatio -> aspectRatio
    MenuState.SubtitleSettings -> subtitleSettings
    MenuState.AutoSkip -> autoSkipChapters
    MenuState.Main, MenuState.Hidden -> settings
}

// `min-height: 2.5rem`, `padding: 6px`.
private val HEADER_MIN_HEIGHT = 40.dp
private val HEADER_PADDING = 6.dp
private val HEADER_SIZE = 13.sp
private val HEADER_BUTTON = 28.dp
private val HEADER_ICON = 16.dp
// `border-bottom: 1px solid rgba(209, 213, 219, 0.2)`.
private val HEADER_RULE = 1.dp
private val HEADER_RULE_COLOR = Color(red = 209, green = 213, blue = 219, alpha = 51)
internal val PANEL_GAP = 4.dp
