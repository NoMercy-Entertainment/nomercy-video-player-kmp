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
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.ui.tv.FluentIcons
import tv.nomercy.player.video.ui.tv.PlayerIconButton

// The pane's name, a hairline, and the way out.
//
// Back where there is somewhere to go back to, close where there is not: the main
// list's cross dismisses the menu and a pane's arrow returns to the main list,
// which is the web's behaviour and the reason a viewer can get out of the
// subtitle settings without losing the settings menu.
@Composable
internal fun MenuHeader(spec: MenuHeaderSpec) {
    val title: String = menuTitle(spec.strings, spec.menu, spec.queue)
    // Both buttons are on the arrow walk — the web's nav takes every `button` in
    // the pane and the header's two are the first it finds. The FIRST of them
    // also carries the pane-open focus: `dialog.show()` lands on the first
    // focusable in the dialog, which is the back arrow where there is one and
    // the close cross on the main list.
    val nav: MenuNav? = LocalMenuNav.current
    val sub: Boolean = spec.menu != MenuState.Main

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HEADER_MIN_HEIGHT)
            .padding(HEADER_PADDING)
            .testTag(MENU_HEADER_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        if (sub) {
            HeaderBack(spec, nav)
        }

        BasicText(
            text = title,
            style = TextStyle(color = Color.White, fontSize = HEADER_SIZE, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )

        PlayerIconButton(
            icon = FluentIcons.Close,
            description = spec.strings.close,
            onClick = { spec.onMenuChange(MenuState.Hidden) },
            buttonSize = HEADER_BUTTON,
            iconSize = HEADER_ICON,
            focusRequester = if (sub) null else nav?.header,
            modifier = Modifier.testTag(MENU_CLOSE_TAG).menuNavEntry(nav),
        )
    }

    // `border-bottom: 1px solid rgba(209, 213, 219, 0.2)`.
    Box(modifier = Modifier.fillMaxWidth().height(HEADER_RULE).background(HEADER_RULE_COLOR))
}

// The way back to the main list, drawn on every pane that is not it.
@Composable
private fun HeaderBack(spec: MenuHeaderSpec, nav: MenuNav?) {
    PlayerIconButton(
        icon = FluentIcons.Back,
        description = spec.strings.back,
        onClick = { spec.onMenuChange(MenuState.Main) },
        buttonSize = HEADER_BUTTON,
        iconSize = HEADER_ICON,
        focusRequester = nav?.header,
        modifier = Modifier.testTag(MENU_BACK_TAG).menuNavEntry(nav),
    )
}

// Which pane a viewer is looking at. The web writes the name into the header, and
// a panel whose header always said "Settings" would be lying three panes deep.
//
// The playlist is the one that cannot answer from the pane alone: `.playlist-title`
// is rewritten per render from what is IN the queue, so the same pane is headed
// "Episodes" over a run of television and "Playlist" over a shelf of films.
private fun menuTitle(strings: MenuStrings, menu: MenuState, queue: List<TvChromeItem>): String =
    when (menu) {
        MenuState.Quality -> strings.quality
        MenuState.Audio -> strings.audio
        MenuState.Subtitle -> strings.subtitles
        MenuState.Speed -> strings.speed
        MenuState.Playlist -> playlistTitle(strings, queue)
        MenuState.AspectRatio -> strings.aspectRatio
        MenuState.SubtitleSettings -> strings.subtitleSettings
        MenuState.AutoSkip -> strings.autoSkipChapters
        MenuState.Main, MenuState.Hidden -> strings.settings
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
