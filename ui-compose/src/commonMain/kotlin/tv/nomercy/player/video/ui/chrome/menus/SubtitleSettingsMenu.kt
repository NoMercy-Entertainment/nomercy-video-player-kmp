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
        Column(modifier = Modifier.testTag(SUBTITLE_PROPERTY_TAG)) {
            chosen.choices().forEach { choice ->
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

    Column {
        SubtitleSetting.entries.forEach { setting ->
            MenuRow(
                "${strings.subtitleSettingLabel(setting)}  ${setting.valueOf(style)}",
                tag = "$ROW_SUBTITLE_SETTING${setting.property}",
                opensSubMenu = true,
            ) {
                open = setting
            }
        }

        // The tenth row, which is not a property: it writes the defaults back
        // and has no pane and no chevron.
        MenuRow(strings.reset, tag = ROW_SUBTITLE_RESET) {
            commands.setSubtitleStyle(SubtitleStyle())
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
