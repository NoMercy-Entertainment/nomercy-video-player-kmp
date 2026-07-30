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
import androidx.compose.runtime.IntState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
                modifier = Modifier.fillMaxSize().repaintOnEachFrame(surface.sink.version),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * Repaint when a frame lands, without recomposing to find out.
 *
 * The bitmap passed to [Image] is now the same object for the whole of a
 * playback — the sink writes each frame into it rather than allocating another —
 * so nothing about this composition changes when the picture does, and on its
 * own it would paint the first frame and then stand still.
 *
 * The counter is read HERE, inside the draw lambda, and that placement is the
 * point. A state read during the draw phase subscribes the DRAW to it: the next
 * frame invalidates one node's paint. Read in the composable body instead and
 * every frame would rebuild the painter and re-measure the layout to arrive at
 * the same size it already was.
 */
private fun Modifier.repaintOnEachFrame(version: IntState): Modifier =
    drawWithContent {
        FrameStats.painted(version.intValue)
        drawContent()
    }
