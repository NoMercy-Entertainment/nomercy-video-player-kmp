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

/**
 * The sink's latest frame, drawn.
 *
 * Split out from [PlayerSurface] so it can be rendered without libVLC. What the
 * picture does over successive frames is the one thing about this file that has
 * gone wrong invisibly — the counters said 24 frames a second while the screen
 * held one image — and a gate for that cannot need a decoder.
 */
@Composable
internal fun FrameCanvas(
    sink: ComposeFrameSink,
    modifier: Modifier,
    scale: ContentScale = ContentScale.Fit,
) {
    // Black, because the alternative is the window's background showing through
    // between the moment the view mounts and the first decoded frame.
    Box(modifier = modifier.background(Color.Black)) {
        // Read in the composition, so a frame arriving publishes a NEW wrapper
        // and Compose derives a new Skia image from it.
        //
        // The wrapper is what changes, not the pixels. Compose caches the skia
        // Image it derives from an ImageBitmap against that instance, and
        // installPixels hands the bitmap a different native pixel store every
        // frame — so a wrapper held across frames draws the store it was
        // derived from, forever, while delivered/drawn both read 23.9/s and the
        // picture on screen never moves. That is what a black player after a
        // seek is: the old store, still being drawn.
        //
        // asComposeImageBitmap WRAPS rather than copies, so this costs one small
        // object per frame. toComposeImageBitmap is the one that allocates and
        // blits, and using it here is what once made the desktop a slideshow.
        val current: ImageBitmap? = sink.frame.value
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().repaintOnEachFrame(sink.version),
                contentScale = scale,
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
