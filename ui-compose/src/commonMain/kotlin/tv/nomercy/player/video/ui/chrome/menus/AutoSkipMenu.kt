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
import tv.nomercy.player.video.ui.chrome.ChromeCommands
import tv.nomercy.player.video.ui.chrome.ChromeState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier

/**
 * On and off, as his `AutoSkipChapterMenu` draws them.
 *
 * Two rows with a mark on the one in effect, not a switch. A viewer arriving
 * from the row above reads which it is without having to interpret a control,
 * and it matches every other pane in this menu.
 */
@Composable
internal fun AutoSkipMenu(
    state: ChromeState,
    commands: ChromeCommands,
    strings: MenuStrings,
    onMenuChange: (MenuState) -> Unit,
) {
    Column(
        modifier = Modifier.padding(MENU_LIST_PADDING),
        verticalArrangement = Arrangement.spacedBy(MENU_LIST_GAP),
    ) {
        MenuRow(strings.on, tag = ROW_AUTO_SKIP_ON, isCurrent = state.autoSkipChapters) {
            commands.setAutoSkipChapters(true)
            onMenuChange(MenuState.Main)
        }

        MenuRow(strings.off, tag = ROW_AUTO_SKIP_OFF, isCurrent = !state.autoSkipChapters) {
            commands.setAutoSkipChapters(false)
            onMenuChange(MenuState.Main)
        }
    }
}
