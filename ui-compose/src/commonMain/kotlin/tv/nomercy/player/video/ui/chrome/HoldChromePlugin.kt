// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import tv.nomercy.player.video.NMVideoPlayer

// The chrome plugin, if the host registered one, gets the live controller for
// as long as this chrome is composed. Without this its holdChrome() is a call
// into null — a handle that answers politely and does nothing, which is worse
// than not having one.
@Composable
internal fun HoldChromePlugin(player: NMVideoPlayer, controller: ChromeController) {
    val plugin: DesktopUiPlugin? = remember(player) { player.getPlugin(DesktopUiPlugin.id) as? DesktopUiPlugin }
    DisposableEffect(plugin, controller) {
        plugin?.controller = controller
        onDispose { if (plugin?.controller === controller) plugin?.controller = null }
    }
}
