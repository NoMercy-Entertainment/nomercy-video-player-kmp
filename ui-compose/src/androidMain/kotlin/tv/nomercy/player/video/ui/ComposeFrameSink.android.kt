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
 * libmpv's picture, on Android.
 *
 * Reached only when the device cannot decode a file itself — ExoPlayer leads
 * the registry and renders through a SurfaceView, which never comes here. What
 * does come here is every Hi10P file in a library, because no Android decoder
 * offers profile 110 and ffmpeg inside libmpv is the only thing on the device
 * that will open one.
 */
internal actual class ComposeFrameSink actual constructor() : VideoFrameSink {

    // RGBA, because ARGB_8888 is RGBA in memory despite its name, and mpv is
    // asked for `rgb0` rather than the desktop's `bgr0`. Swizzling four bytes
    // per pixel per frame on the CPU is the alternative, and on a phone it is
    // the difference between a decode that keeps up and one that does not.
    override val pixelOrder: PixelOrder = PixelOrder.RGBA

    actual val frame: MutableState<ImageBitmap?> = mutableStateOf(null)

    actual val version: MutableIntState = mutableIntStateOf(0)

    // One bitmap for the life of a picture size, written in place.
    //
    // A bitmap per frame is a full-frame allocation at the decode rate, which on
    // the desktop turned 24 frames a second into 8 as the collector caught up.
    // A phone has less headroom than the desktop did, not more.
    private var bitmap: Bitmap? = null
    private var pixels: ByteArray = ByteArray(0)

    // The picture on screen is left alone until a frame of the new size lands:
    // blanking here would put a black flash into every resize.
    override fun format(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        pixels = ByteArray(width * height * BYTES_PER_PIXEL)
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    override fun display(picture: ByteBuffer) {
        val target: Bitmap = bitmap ?: return
        if (pixels.isEmpty()) return

        val reader: ByteBuffer = picture.duplicate()
        reader.rewind()

        // Short of a whole frame means the size was renegotiated between the
        // allocation and this call. Copying what arrived would draw the top of
        // the new picture over the bottom of the old one; refusing it holds the
        // last complete frame, which reads as a still rather than a tear.
        if (reader.remaining() < pixels.size) return
        reader.get(pixels, 0, pixels.size)

        target.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))

        // A new wrapper every frame, so the state Compose is watching actually
        // changes. asImageBitmap wraps rather than copies, so this costs an
        // object and not a picture.
        frame.value = target.asImageBitmap()
        version.value += 1
    }

    // Back to nothing, so the canvas draws its black instead of the last item.
    // The version still moves: a draw subscribed to it has to be told that what
    // it was drawing is gone.
    override fun clear() {
        frame.value = null
        version.value += 1
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
    // Nothing to release: one bitmap is refilled for the whole of a playback,
    // so there is no pile of decoded pictures waiting for somebody to free them.
    @Suppress("EmptyFunctionBlock")
    actual fun drawn(painted: Int) {
    }

}
