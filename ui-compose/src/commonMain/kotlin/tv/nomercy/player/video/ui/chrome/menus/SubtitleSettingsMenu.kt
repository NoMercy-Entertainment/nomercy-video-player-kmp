// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import tv.nomercy.player.video.ui.chrome.ChromeCommands
import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.video.ui.chrome.ChromeState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

// Nine properties and a reset, in the web's SETTING_ROWS order.
//
// Each row shows what it currently reads, which is the whole difference between
// a list of options and a list of settings: without the value a viewer has to
// open every pane to find out what their subtitles are set to.
@Composable
internal fun SubtitleSettingsMenu(
    state: ChromeState,
    commands: ChromeCommands,
    strings: MenuStrings,
) {
    var open: SubtitleSetting? by remember { mutableStateOf(null) }
    val style: SubtitleStyle = state.subtitleStyle

    val chosen: SubtitleSetting? = open
    if (chosen != null) {
        // A scrolling list with the same padding and gaps as every other pane.
        // A plain Column gave the font list none of either, so it sat flush to
        // the card's edges and ran past its bottom with no way to reach the
        // faces underneath.
        MenuPane(modifier = Modifier.testTag(SUBTITLE_PROPERTY_TAG)) {
            items(chosen.choices()) { choice ->
                MenuRow(choice, isCurrent = choice == chosen.valueOf(style)) {
                    commands.setSubtitleStyle(chosen.applied(style, choice))
                    // Back to the list rather than out of the menu. Somebody
                    // setting up subtitles is usually changing more than one
                    // thing, and the web's property pane returns here too.
                    open = null
                }
            }
        }
        return
    }

    MenuPane {
        items(SubtitleSetting.entries) { setting ->
            // The value belongs in the row's tail, where every other menu puts
            // it. Concatenated into the label it read as one long left-aligned
            // string and these rows alone looked unlike the rest of the card.
            MenuRow(
                strings.subtitleSettingLabel(setting),
                tag = "$ROW_SUBTITLE_SETTING${setting.property}",
                opensSubMenu = true,
                subLabel = setting.valueOf(style),
            ) {
                open = setting
            }
        }

        // The tenth row, which is not a property: it writes the defaults back
        // and has no pane and no chevron.
        item {
            MenuRow(strings.reset, tag = ROW_SUBTITLE_RESET) {
                commands.setSubtitleStyle(SubtitleStyle())
            }
        }
    }
}

// Which label belongs to which row, kept beside the strings rather than on the
// enum: the enum is the web's property list and this is a translation table, and
// putting the words on the enum would make every new locale a code change there.
internal fun MenuStrings.subtitleSettingLabel(setting: SubtitleSetting): String = when (setting) {
    SubtitleSetting.Font -> subtitleFont
    SubtitleSetting.TextSize -> subtitleTextSize
    SubtitleSetting.TextColor -> subtitleTextColor
    SubtitleSetting.TextOpacity -> subtitleTextOpacity
    SubtitleSetting.EdgeStyle -> subtitleEdgeStyle
    SubtitleSetting.BackgroundColor -> subtitleBackgroundColor
    SubtitleSetting.BackgroundOpacity -> subtitleBackgroundOpacity
    SubtitleSetting.AreaColor -> subtitleAreaColor
    SubtitleSetting.AreaOpacity -> subtitleAreaOpacity
}
