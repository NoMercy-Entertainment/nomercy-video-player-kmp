// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.tv.TvChromeStrings

/**
 * The player's notice band, for a host that draws its own controls.
 *
 * [ChromeStatusText] reaches the same pixels, but only through the library's own
 * chrome. A consumer with its own transport bar — which both of ours have on the
 * phone — mounted no chrome at all, so every notice the message channel produced
 * went nowhere: loading, buffering, a failure, and the track a viewer had just
 * chosen. Reported as "no indication of the subtitles loading".
 */
@Composable
public fun PlayerMessageOverlay(
    player: NMVideoPlayer,
    strings: TvChromeStrings,
    modifier: Modifier = Modifier,
) {
    val message: ChromeMessage = rememberChromeMessage(player, strings) ?: return

    Box(modifier.fillMaxSize()) {
        PlayerMessageBand(message.text)
    }
}
