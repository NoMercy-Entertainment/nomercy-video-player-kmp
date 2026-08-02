// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val W = 4
private const val H = 4


// The frame conversion on its own, with a buffer this test wrote.
//
// libVLC hands over BGRA bytes and Compose wants an ImageBitmap; everything that
// can go wrong between those two — the byte order, the row stride, whether the
// pixels survive the trip at all — goes wrong silently and shows up as a black
// player. Feeding it a known buffer is the only way to see which.
class ComposeFrameSinkTest {

    private fun bgra(blue: Int, green: Int, red: Int): List<Byte> =
        listOf(blue.toByte(), green.toByte(), red.toByte(), 0xFF.toByte())

    private fun bufferOf(bytes: List<Byte>): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            bytes.forEach { put(it) }
            rewind()
        }

    // One corner pixel in the given colour, everything else black, in the layout
    // libVLC uses.
    private fun frame(blue: Int, green: Int, red: Int): ByteBuffer {
        val bytes: MutableList<Byte> = mutableListOf()
        repeat(W * H) { index ->
            bytes += if (index == 0) bgra(blue, green, red) else bgra(0, 0, 0)
        }
        return bufferOf(bytes)
    }

    private fun redFrame(): ByteBuffer = frame(blue = 0, green = 0, red = 0xFF)

    private fun greenFrame(): ByteBuffer = frame(blue = 0, green = 0xFF, red = 0)

    @Test
    fun aFrameArrivesAsPixelsRatherThanAsBlack() {
        val sink = ComposeFrameSink()
        sink.format(W, H)

        sink.accept(redFrame())

        val frame: ImageBitmap = assertNotNull(sink.frame.value, "no frame was produced")
        val pixels = frame.toPixelMap()
        assertEquals(1f, pixels[0, 0].red, "the red pixel did not survive the conversion")
        assertEquals(0f, pixels[1, 0].red, "a black pixel came back red")
    }

    // Each frame is published as its own immutable bitmap, and the one before it
    // is still readable.
    //
    // This asserted the opposite until 2026-08-02 — one bitmap for the whole
    // playback, refilled in place — and it was written to protect the desktop's
    // frame rate, which is a real thing to protect: an earlier version allocated
    // three native buffers and rasterised the picture twice per frame at 1080p,
    // and delivery collapsed from 24 frames a second to 8.
    //
    // It was written on the wrong noun. Skia's `setImmutable` is one way, and it
    // is what lets the render thread share a frame's pixels instead of copying
    // them — so a bitmap that has been shown can never be refilled. Refilling one
    // anyway gave the render thread `Failed to Image::makeFromBitmap` on a pixel
    // store being replaced underneath it, and before that a picture that simply
    // stopped moving while every counter in this file read 23.9 frames a second.
    //
    // What replaces the guarantee is the test below: the cost was never the
    // bitmap object, it was native memory nobody freed.
    @Test
    fun eachFrameIsPublishedAsItsOwnImmutableBitmap() {
        val sink = ComposeFrameSink()
        sink.format(W, H)

        sink.accept(redFrame())
        val first: ImageBitmap = assertNotNull(sink.frame.value)

        sink.accept(greenFrame())
        val second: ImageBitmap = assertNotNull(sink.frame.value)

        assertTrue(first !== second, "the wrapper was reused, so Compose draws the image it cached")
        assertTrue(second.asSkiaBitmap().isImmutable, "a mutable frame is copied on every draw")
        assertEquals(1f, second.toPixelMap()[0, 0].green, "the second frame never landed")
        assertEquals(0f, second.toPixelMap()[0, 0].red, "the first frame is still showing")
        assertEquals(1f, first.toPixelMap()[0, 0].red, "the frame before it was overwritten in place")
    }

    // And because the bitmap no longer changes identity, something else has to
    // tell Compose to paint again. The counter is that something, and a frame
    // that does not advance it is a frame the viewer never sees.
    @Test
    fun eachAcceptedFrameAdvancesTheCounter() {
        val sink = ComposeFrameSink()
        sink.format(W, H)

        val start: Int = sink.version.value
        sink.accept(redFrame())
        sink.accept(greenFrame())

        assertEquals(start + 2, sink.version.value, "two frames did not ask for two repaints")
    }

    // Pixels are swapped in whole, so a draw racing the decoder sees one frame or
    // the other rather than the top of one over the bottom of the last. Asserting
    // it directly would need the race; asserting the store was replaced rather
    // than overwritten in place is the same guarantee, checkable.
    @Test
    fun aFrameIsCompleteBeforeItCanBeDrawn() {
        val sink = ComposeFrameSink()
        sink.format(W, H)

        sink.accept(redFrame())
        val first: Int = assertNotNull(sink.frame.value).pixelStoreId()

        sink.accept(greenFrame())
        val second: Int = assertNotNull(sink.frame.value).pixelStoreId()

        assertTrue(first != second, "the new frame was written over the pixels being drawn")
    }

    // A reallocation must not blank what is on screen. The bitmap on screen is
    // reused frame to frame, so a resize takes a new one rather than reallocating
    // this one out from under the compositor.
    @Test
    fun renegotiatingTheFormatDoesNotBlankTheLastFrame() {
        val sink = ComposeFrameSink()
        sink.format(W, H)
        sink.accept(redFrame())

        val showing: ImageBitmap = assertNotNull(sink.frame.value)

        sink.format(W * 2, H * 2)

        assertEquals(1f, showing.toPixelMap()[0, 0].red, "the picture on screen went black")
    }

    // A buffer that arrives short of a full frame is refused rather than drawn.
    // Half a new picture over half an old one is a torn frame; keeping the last
    // good one is a still — and refusing it must not ask for a repaint either.
    @Test
    fun aPartialFrameIsRefused() {
        val sink = ComposeFrameSink()
        sink.format(W, H)
        sink.accept(redFrame())

        val showing: Int = sink.version.value
        sink.accept(ByteBuffer.allocateDirect(W * BYTES_PER_PIXEL))

        assertEquals(showing, sink.version.value, "a short buffer asked for a repaint")
        assertEquals(
            1f,
            assertNotNull(sink.frame.value).toPixelMap()[0, 0].red,
            "a short buffer replaced the frame",
        )
    }
}

// Skia's generation id, which tracks the pixel store rather than the bitmap:
// installing a new store advances it, and writing over the store in place does
// not. Two frames sharing one id is what tearing looks like from here.
private fun ImageBitmap.pixelStoreId(): Int = asSkiaBitmap().generationId

private const val BYTES_PER_PIXEL = 4
