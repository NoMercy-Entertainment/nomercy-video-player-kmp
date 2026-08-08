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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import tv.nomercy.player.video.Stretching
import androidx.compose.ui.layout.onSizeChanged

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
public actual fun PlayerSurface(
    surface: VideoSurface,
    modifier: Modifier,
    stretching: Stretching,
) {
    // The measured size goes to the engine on every layout pass. It is what caps
    // the ladder: a 3840-wide rendition into a pane 372 device-pixels tall was
    // holding delivery at a sixth of the clip's rate, and the engine cannot ask
    // because it renders into a buffer rather than into this box. Device pixels
    // already — Compose reports layout in pixels, so there is no density factor to
    // apply here the way the web multiplies by devicePixelRatio.
    FrameCanvas(
        sink = surface.sink,
        modifier = modifier.onSizeChanged { size ->
            surface.backend?.surfaceSize(size.width, size.height)
        },
        scale = contentScaleOf(stretching),
    )
}

// The web's object-fit values, as Compose spells them.
//
// This was ContentScale.Fit, written down and never a variable, so a viewer
// cycling the aspect ratio changed a field, raised an event and watched the same
// letterboxed picture. `none` is Compose's None, which draws the frame at its own
// size rather than scaling it at all.
private fun contentScaleOf(stretching: Stretching): ContentScale = when (stretching) {
    Stretching.Uniform -> ContentScale.Fit
    Stretching.Fill -> ContentScale.FillBounds
    Stretching.ExactFit -> ContentScale.Crop
    Stretching.None -> ContentScale.None
}
