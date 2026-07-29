// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

// libVLC's frames, turned into something Compose can draw.
//
// This is the answer to the seam the plan called unproven. An embedded surface
// hands libVLC a native window handle, and a native window on the desktop paints
// above everything the toolkit draws — the video would cover the play button
// rather than sit under it, and no amount of Compose z-ordering would move it,
// because the two are not in the same compositor at all.
//
// Rendering into a buffer sidesteps the question instead of fighting it. The
// frame becomes an ImageBitmap like any other, Compose composites it with the
// controls the same way it composites everything else, and there is no Swing in
// the window. It costs one copy per frame, which is the price of compositing at
// all.
internal class ComposeFrameSink : RenderCallback, BufferFormatCallback {

    val frame: MutableState<ImageBitmap?> = mutableStateOf(null)

    private var width: Int = 0
    private var height: Int = 0

    // Reused across frames. Allocating per frame at twenty-four a second is how
    // a player turns into a garbage collector with a picture on it.
    private var pixels: ByteArray = ByteArray(0)
    private var info: ImageInfo = ImageInfo(0, 0, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
    private var rowBytes: Int = 0

    // RV32 is BGRA in memory on a little-endian machine, which is exactly what
    // Skia calls BGRA_8888 — so the frame lands with no conversion, only a copy.
    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
        width = sourceWidth
        height = sourceHeight
        rowBytes = sourceWidth * BYTES_PER_PIXEL
        pixels = ByteArray(rowBytes * sourceHeight)
        info = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        return RV32BufferFormat(sourceWidth, sourceHeight)
    }

    override fun newFormatSize(
        bufferWidth: Int,
        bufferHeight: Int,
        displayWidth: Int,
        displayHeight: Int,
    ): Unit = Unit

    override fun allocatedBuffers(buffers: Array<out ByteBuffer>): Unit = Unit

    override fun lock(mediaPlayer: MediaPlayer): Unit = Unit

    override fun unlock(mediaPlayer: MediaPlayer): Unit = Unit

    override fun display(
        mediaPlayer: MediaPlayer,
        nativeBuffers: Array<out ByteBuffer>,
        bufferFormat: BufferFormat,
        displayWidth: Int,
        displayHeight: Int,
    ): Unit = accept(nativeBuffers[0])

    // The buffer is libVLC's and it reuses it, so the frame has to be copied out
    // before this returns. Holding the buffer instead would draw whatever the
    // decoder happened to be writing.
    //
    // Separate from display() because everything that can go wrong here — the
    // byte order, the row stride, whether the pixels survive the trip at all —
    // goes wrong silently and shows up as a black player. A function taking a
    // buffer can be handed a known one and checked; a callback taking a
    // MediaPlayer cannot.
    internal fun accept(source: ByteBuffer) {
        if (width == 0 || height == 0) return

        val reader: ByteBuffer = source.duplicate()
        reader.rewind()

        // Short of a whole frame means the format was renegotiated between the
        // allocation and this callback. Copying what arrived leaves the tail of
        // the buffer holding the previous picture, so the frame drawn is the top
        // of the new one over the bottom of the old — a tear. Refusing it keeps
        // the last complete frame up, which reads as a still rather than a fault.
        if (reader.remaining() < pixels.size) return

        reader.get(pixels, 0, pixels.size)

        // Data.makeFromBytes COPIES, and that is the whole point.
        //
        // Image.makeRaster over the array WRAPS it. Skia then holds a view of the
        // one buffer this class overwrites on every frame, so the bitmap Compose
        // is drawing and the buffer libVLC is filling were the same memory: every
        // frame tore against the next, and any reallocation blanked the picture
        // that was already on screen. That is the black player after a window
        // resize — getBufferFormat runs again, `pixels` becomes a fresh array of
        // zeroes, and the bitmap already handed to Compose was pointing at it.
        frame.value = Image.makeRaster(info, pixels, rowBytes).toComposeImageBitmap()
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}
