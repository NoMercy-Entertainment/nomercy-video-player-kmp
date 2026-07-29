// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Draws whatever the surface's sink last decoded.
 *
 * It does not attach anything. [VideoSurface] owns its sink and attached it
 * before playback could start, because libVLC chooses a video output at the
 * first play and a surface handed to it afterwards is ignored for that media.
 * This composable mounts after the host has loaded an item, so attaching here
 * was always a race — and it was always losing.
 */
@Composable
public actual fun PlayerSurface(surface: VideoSurface, modifier: Modifier) {
    // Black, because the alternative is the window's background showing through
    // between the moment the view mounts and the first decoded frame.
    Box(modifier = modifier.background(Color.Black)) {
        val current: ImageBitmap? = surface.sink.frame.value
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
