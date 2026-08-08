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
import tv.nomercy.player.core.ports.VideoFrameSink
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
internal class SkiaFrameSink : VideoFrameSink {

    /**
     * The frame Compose draws, republished on every one.
     *
     * A new Bitmap each time, and it has to be: Skia's `setImmutable` is one way,
     * and immutability is what lets the render thread share a frame's pixels
     * instead of copying them — so a bitmap that has been shown can never be
     * refilled. Refilling one anyway is what this did until 2026-08-02, and it
     * cost the desktop its picture twice over: first as a still that never moved
     * while every counter here read 23.9 frames a second, then as
     * `Failed to Image::makeFromBitmap` thrown on the render thread, deriving an
     * image from a pixel store being replaced underneath it.
     *
     * `Bitmap.asComposeImageBitmap()` WRAPS, so the wrapper costs an object.
     * `Image.toComposeImageBitmap()` does not: it allocates a second bitmap,
     * opens a raster canvas over it and blits the whole picture across — three
     * ~8 MB native buffers per 1080p frame and two rasterisations, none freed
     * until a cleaner happened to run. Measured on the first version of this
     * file: 24 frames a second at 6.5 ms conversion for eight seconds, then
     * delivery collapsing to 8/s with the same conversion taking 25-33 ms. That
     * is why [pixels] is reused and why nothing here rasterises.
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
    // OPAQUE, not PREMUL, and the difference is a whole picture.
    //
    // Decoded video has no transparency on either engine, and the fourth byte
    // of each pixel is padding rather than alpha — libVLC's RV32 happens to
    // leave 0xFF there and mpv's `bgr0` leaves 0. Read as PREMUL that makes
    // every mpv pixel fully transparent, so a correctly decoded frame arrives,
    // Skia composites nothing, and the pane is black with the frame counter
    // climbing. OPAQUE tells Skia to ignore the byte, which is true of both.
    private var info: ImageInfo = ImageInfo(0, 0, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
    private var rowBytes: Int = 0

    // The engine delivers RV32, which is BGRA in memory on a little-endian
    // machine and exactly what Skia calls BGRA_8888 — so the frame lands with no
    // conversion, only a copy.
    //
    // The picture on screen is left alone. Whatever was last published stays up
    // until the first frame of the new size arrives; blanking here would put a
    // black flash into every resize.
    override fun format(width: Int, height: Int) {
        rowBytes = width * BYTES_PER_PIXEL
        pixels = ByteArray(rowBytes * height)
        info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        FrameStats.format(width, height)
    }

    override fun display(picture: ByteBuffer): Unit = accept(picture)

    // Back to nothing, so FrameCanvas draws its black instead of the last item.
    // The version still moves: a draw subscribed to it has to be told that what
    // it was drawing is gone.
    override fun clear() {
        frame.value = null
        version.value += 1

        // Both of them, because an item change is where a whole picture's worth
        // of native memory would otherwise sit until the next film happened to
        // publish two frames. `displayed` is dropped without closing: the canvas
        // may still hold the ImageBitmap wrapping it for one more draw, and
        // closing a bitmap out from under a live draw crashes the renderer
        // rather than throwing anything catchable.
        // Every one of them. The canvas has been told there is nothing to draw,
        // so nothing it holds can be painted again.
        held.forEach { (_, bitmap) -> bitmap.close(); closed += 1 }
        held.clear()
    }

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
        // The whole frame installed at once, so a draw racing this call sees a
        // complete picture and never the top of one over the bottom of another.
        //
        // setImmutable before it is published, and it is what lets the draw
        // share these pixels instead of copying them: Skia's makeFromBitmap
        // copies a MUTABLE bitmap, which would put a full-frame allocation back,
        // this time on the render thread.
        val next = Bitmap()
        allocated += 1
        next.installPixels(info, pixels, rowBytes)
        next.setImmutable()

        // asComposeImageBitmap WRAPS. toComposeImageBitmap is the one that
        // allocates a second bitmap and blits the picture into it, and using it
        // here is what once made the desktop a slideshow.
        frame.value = next.asComposeImageBitmap()

        // Held until the canvas says it has drawn past them.
        //
        // installPixels copies into a native pixel ref — eight megabytes for
        // 1080p, thirty-three for 4K — and a Skia Bitmap frees that only when
        // its Java wrapper is collected. The wrapper is a few dozen bytes, so
        // the heap barely moves, the collector never sees a reason to run, and
        // the native side climbs without limit: twenty gigabytes and a frozen
        // machine.
        //
        // Freeing on a frame count instead was a crash, not a fix.
        // asComposeImageBitmap WRAPS the Bitmap rather than copying it, so a
        // closed bitmap leaves the render thread reading freed memory —
        // EXCEPTION_ACCESS_VIOLATION outside the JVM, which no catch reaches.
        // Two frames of slack looked like plenty and is still a race.
        //
        // So nothing is guessed: [drawn] is called from the draw phase with the
        // version it painted, and only frames the canvas has already moved past
        // are released.
        held += version.value + 1 to next
    }

    /**
     * The canvas reporting which frame it has painted.
     *
     * Everything older than that is unreachable — the canvas draws one frame at
     * a time and never goes back — so this is the only moment at which freeing
     * a picture is provably safe rather than probably safe.
     */
    fun drawn(painted: Int) {
        val iterator = held.iterator()
        while (iterator.hasNext()) {
            val (published, bitmap) = iterator.next()
            if (published >= painted) continue

            bitmap.close()
            closed += 1
            iterator.remove()
        }
    }

    // The two bitmaps behind the one being published. Not a pool being reused —
    // Compose caches the Skia image it derives against the ImageBitmap INSTANCE,
    // so refilling one already published is a picture that stops moving while
    // every counter climbs. These are kept only so they can be freed.
    // Published frames the canvas has not yet reported painting past, newest
    // last. Bounded in practice by how far the renderer trails the decoder,
    // which is one or two frames.
    private val held: MutableList<Pair<Int, Bitmap>> = mutableListOf()

    // How many decoded pictures this sink is still holding native memory for.
    //
    // A count rather than a byte total, because a byte total measures the
    // collector's mood — it may free nothing and still be correct — while the
    // number of undead bitmaps is exactly what went wrong and is exact.
    internal fun liveBitmaps(): Int = allocated - closed

    // Counted at the two places that change it rather than derived from the two
    // references, because the references are bounded by construction and a test
    // reading them could never go red — which is the shape of check that let
    // this ship.
    private var allocated: Int = 0
    private var closed: Int = 0

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}
