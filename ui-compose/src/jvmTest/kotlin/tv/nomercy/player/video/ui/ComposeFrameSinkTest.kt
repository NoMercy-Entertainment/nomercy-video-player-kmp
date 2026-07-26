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
}
