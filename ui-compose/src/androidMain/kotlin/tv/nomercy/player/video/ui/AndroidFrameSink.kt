// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import android.graphics.Bitmap
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import tv.nomercy.player.core.ports.PixelOrder
import tv.nomercy.player.core.ports.VideoFrameSink
import java.nio.ByteBuffer

/**
 * Where a software engine's picture goes on Android.
 *
 * A second sink rather than the desktop one because the desktop one is Skia —
 * `org.jetbrains.skia.Bitmap` — and Compose on Android draws through
 * `android.graphics`. Sharing the file would mean shipping Skia to a phone that
 * already has a rasteriser.
 *
 * RGBA, where the desktop asks for BGRA. `ARGB_8888` is red-green-blue-alpha in
 * MEMORY despite the name, and the engine is asked for `rgb0` through
 * [pixelOrder] rather than swizzled here — a per-frame channel swap on the CPU
 * is 8 megabytes of pointless work per 1080p frame, and mpv accepts either
 * order for free.
 */
internal class AndroidFrameSink : VideoFrameSink {

    val frame: MutableState<ImageBitmap?> = mutableStateOf(null)

    // A frame counter beside the frame, because the bitmap is REUSED between
    // frames and Compose compares by identity: same object, no redraw, and the
    // picture stands still while the counters climb. The desktop sink carries
    // the same number for the same reason.
    val version: MutableIntState = mutableIntStateOf(0)

    override val pixelOrder: PixelOrder = PixelOrder.RGBA

    private var bitmap: Bitmap? = null

    override fun format(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        if (bitmap.matches(width, height)) return

        // A new bitmap only when the SIZE changes, and the old one is not
        // recycled: Compose may still be drawing the last frame from it, and
        // recycling underneath a live draw is a crash in the renderer rather
        // than an exception anybody can catch.
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    override fun display(picture: ByteBuffer) {
        val target: Bitmap = bitmap ?: return

        picture.rewind()
        target.copyPixelsFromBuffer(picture)

        // A NEW wrapper each frame. Compose compares by identity to decide
        // whether to redraw, so publishing the same wrapper again is a frame
        // that decoded, arrived, and never reached the screen — the counters
        // climb and the picture stands still.
        frame.value = target.asImageBitmap()
        version.intValue += 1
    }

    override fun clear() {
        frame.value = null
        version.intValue += 1
    }
}

private fun Bitmap?.matches(width: Int, height: Int): Boolean =
    this != null && this.width == width && this.height == height
