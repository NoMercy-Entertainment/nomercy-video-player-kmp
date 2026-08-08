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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

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
    // The FRAME and its counter, not a particular sink type.
    //
    // There are two sinks now and they cannot be one: the desktop's is Skia and
    // Android's is android.graphics, and the byte order each wants is opposite.
    // What they have in common is exactly this — a bitmap and a number that
    // changes when it does — so that is what the canvas takes, and neither
    // platform's sink leaks into the other's artifact.
    frame: ImageBitmap?,
    version: IntState,
    modifier: Modifier,
    scale: ContentScale = ContentScale.Fit,
) {
    // Black, because the alternative is the window's background showing through
    // between the moment the view mounts and the first decoded frame.
    // Clipped, which Crop and FillBounds make load-bearing rather than tidy.
    //
    // A scale that draws wider than its box paints straight over whatever is
    // beside it: cycling the aspect ratio pushed the picture out of the player
    // and across the rest of the page. The browser never had this because a
    // video element's object-fit is clipped by the element itself.
    Box(modifier = modifier.clipToBounds().background(Color.Black)) {
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
        val current: ImageBitmap? = frame
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().repaintOnEachFrame(version),
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
