// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
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
// the window. It costs a copy per frame, which is the price of compositing at
// all — but only a copy, and that is the whole subject of this file.
internal class ComposeFrameSink : RenderCallback, BufferFormatCallback {

    /**
     * The bitmap Compose draws, which is the SAME object for as long as the
     * picture stays one size.
     *
     * It used to be a new ImageBitmap per frame, and that is what made the
     * desktop a slideshow. `Image.toComposeImageBitmap()` does not wrap — it
     * allocates a second bitmap, opens a raster canvas over it and blits the
     * whole picture across. With the copy out of libVLC's buffer and the Data
     * that `makeRaster` takes, one frame at 1080p allocated three ~8 MB native
     * buffers and rasterised the picture twice, and none of it was freed until
     * the JVM happened to run a cleaner — which it has no reason to do, because
     * native memory is invisible to the heap it decides on.
     *
     * The measurement: 24 delivered frames per second for the first eight
     * seconds and conversion at 6.5 ms, then delivery collapsing to 8/s with the
     * same conversion taking 25-33 ms. Identical work getting four times slower
     * is not a cost, it is pressure.
     */
    val frame: MutableState<ImageBitmap?> = mutableStateOf(null)

    /**
     * Which frame is in [frame], so a redraw can be asked for without a
     * recomposition.
     *
     * A reused bitmap has a problem the old code did not: mutating pixels
     * changes nothing Compose is watching, so nothing repaints. Bumping a
     * counter and reading it inside the DRAW phase is the cheap half of the fix
     * — the composition stands, the painter is not rebuilt, and the only thing
     * invalidated is the one node that has to paint again.
     */
    val version: MutableIntState = mutableIntStateOf(0)

    private var pixels: ByteArray = ByteArray(0)
    private var bitmap: Bitmap = Bitmap()
    private var info: ImageInfo = ImageInfo(0, 0, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
    private var rowBytes: Int = 0

    // RV32 is BGRA in memory on a little-endian machine, which is exactly what
    // Skia calls BGRA_8888 — so the frame lands with no conversion, only a copy.
    //
    // A NEW Bitmap rather than a reallocated one, because the bitmap already
    // handed to Compose is on screen: reusing it here would blank the live
    // picture between this call and the first frame of the new size.
    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
        rowBytes = sourceWidth * BYTES_PER_PIXEL
        pixels = ByteArray(rowBytes * sourceHeight)
        info = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        bitmap = Bitmap()
        bitmap.allocPixels(info)
        frame.value = bitmap.asComposeImageBitmap()
        FrameStats.format(sourceWidth, sourceHeight)
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
        val startedAt: Long = System.nanoTime()
        if (!readOut(source)) return

        val readAt: Long = System.nanoTime()
        handOver()
        version.value += 1
        FrameStats.delivered(readAt - startedAt, System.nanoTime() - readAt)
    }

    private fun readOut(source: ByteBuffer): Boolean {
        if (pixels.isEmpty()) return false

        val reader: ByteBuffer = source.duplicate()
        reader.rewind()

        // Short of a whole frame means the format was renegotiated between the
        // allocation and this callback. Copying what arrived leaves the tail of
        // the buffer holding the previous picture, so the frame drawn is the top
        // of the new one over the bottom of the old — a tear. Refusing it keeps
        // the last complete frame up, which reads as a still rather than a fault.
        if (reader.remaining() < pixels.size) return false

        reader.get(pixels, 0, pixels.size)
        return true
    }

    private fun handOver() {
        // installPixels replaces the bitmap's pixel store outright with one it
        // has already filled, so a draw racing this call sees either the whole
        // previous frame or the whole new one — never half of each. Writing over
        // pixels the compositor is reading is what would tear.
        //
        // setImmutable on every frame, not once: the flag lives on the pixel
        // store, so a new store arrives unmarked. It is what lets the draw share
        // those pixels instead of copying them — Skia's makeFromBitmap copies a
        // MUTABLE bitmap, which would put a full-frame allocation back, this
        // time on the render thread.
        bitmap.installPixels(info, pixels, rowBytes)
        bitmap.setImmutable()
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}
