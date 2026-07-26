// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

@Composable
public actual fun PlayerSurface(surface: VideoSurface, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                // The library draws the video and nothing else. Media3's own
                // controller would be a second set of controls fighting the
                // one above it, and an app replacing this view's chrome would
                // have to find and disable it.
                useController = false
                player = surface.exoPlayer
            }
        },
        // Re-attached rather than rebuilt: a recomposition that dropped the
        // view would tear down the output and restart the video.
        update = { view -> view.player = surface.exoPlayer },
        onRelease = { view -> view.player = null },
    )
}
