// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

    // Top-left pixel red, everything else black, in the layout libVLC uses.
    private fun buffer(): ByteBuffer {
        val bytes: MutableList<Byte> = mutableListOf()
        repeat(W * H) { index ->
            bytes += if (index == 0) bgra(0, 0, 0xFF) else bgra(0, 0, 0)
        }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            bytes.forEach { put(it) }
            rewind()
        }
    }

    @Test
    fun aFrameArrivesAsPixelsRatherThanAsBlack() {
        val sink = ComposeFrameSink()
        sink.getBufferFormat(W, H)

        sink.accept(buffer())

        val frame: ImageBitmap = assertNotNull(sink.frame.value, "no frame was produced")
        val pixels = frame.toPixelMap()
        assertEquals(1f, pixels[0, 0].red, "the red pixel did not survive the conversion")
        assertEquals(0f, pixels[1, 0].red, "a black pixel came back red")
    }

    // The frame Compose is holding must not change when the next one arrives.
    //
    // This is characterisation, not a fix. `Image.makeRaster` over a ByteArray
    // looked like it would WRAP the buffer this class overwrites every frame,
    // which would alias the bitmap being drawn to the bytes being filled — and
    // the theory was that a reallocation then blanked the live picture, which
    // would have explained the black player after a window resize.
    //
    // It does not. Skia copies, and this test passes with or without an explicit
    // one, which is what killed the theory. It stays because the guarantee is
    // load-bearing and undocumented: the day makeRaster starts wrapping, every
    // frame tears and this is the only thing that would say so.
    @Test
    fun aFrameAlreadyHandedOverSurvivesTheNextOne() {
        val sink = ComposeFrameSink()
        sink.getBufferFormat(W, H)

        sink.accept(buffer())
        val first: ImageBitmap = assertNotNull(sink.frame.value)

        // A second frame with the top-left pixel green instead of red.
        val second: MutableList<Byte> = mutableListOf()
        repeat(W * H) { index -> second += if (index == 0) bgra(0, 0xFF, 0) else bgra(0, 0, 0) }
        sink.accept(
            ByteBuffer.allocateDirect(second.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                second.forEach { put(it) }
                rewind()
            },
        )

        assertEquals(1f, first.toPixelMap()[0, 0].red, "the first frame changed under Compose")
        assertEquals(0f, first.toPixelMap()[0, 0].green, "the second frame bled into the first")
    }

    // And a reallocation must not blank what is on screen. Also characterisation
    // — it already did not, so the black-after-resize has another cause.
    @Test
    fun renegotiatingTheFormatDoesNotBlankTheLastFrame() {
        val sink = ComposeFrameSink()
        sink.getBufferFormat(W, H)
        sink.accept(buffer())

        val showing: ImageBitmap = assertNotNull(sink.frame.value)

        sink.getBufferFormat(W * 2, H * 2)

        assertEquals(1f, showing.toPixelMap()[0, 0].red, "the picture on screen went black")
    }

    // A buffer that arrives short of a full frame is refused rather than drawn.
    // Half a new picture over half an old one is a torn frame; keeping the last
    // good one is a still.
    @Test
    fun aPartialFrameIsRefused() {
        val sink = ComposeFrameSink()
        sink.getBufferFormat(W, H)
        sink.accept(buffer())

        val showing: ImageBitmap = assertNotNull(sink.frame.value)
        sink.accept(ByteBuffer.allocateDirect(W * BYTES_PER_PIXEL))

        assertEquals(showing, sink.frame.value, "a short buffer replaced the frame")
    }
}

private const val BYTES_PER_PIXEL = 4
