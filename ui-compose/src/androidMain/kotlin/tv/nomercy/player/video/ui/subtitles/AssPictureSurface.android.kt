// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import tv.nomercy.player.video.subtitles.AssSurfaceFrame
import java.nio.IntBuffer

// Red and blue are swapped on the way in, and that is not a detail.
//
// An ARGB_8888 bitmap stores its bytes R,G,B,A in memory, and
// copyPixelsFromBuffer is a memcpy: a little-endian 0xAARRGGBB int arrives as
// B,G,R,A and is read back as red where blue was. The desktop surface says the
// same thing from the other side — Skia is told BGRA_8888 precisely because
// that IS the little-endian spelling of the compositor's int — and this was the
// one platform that never converted. Nothing fails. No Game No Life's dialogue
// outline is authored &H00833F39, a dark navy, and it drew warm orange on a
// phone while the same line off-screen drew navy; a dark outline turned pale is
// white text with nothing holding it off the picture.
//
// Swapped over the changed rows only, the way the desktop converts only what
// moved. A full-frame pass is eight million channel swaps to find the few
// hundred thousand pixels a line of dialogue touches.
//
// Software ARGB_8888, and that is not a detail either: a hardware bitmap cannot
// be read back, so one allocated as such renders black with nothing reported —
// which looks exactly like a subtitle that failed to arrive.
internal actual class AssPictureSurface actual constructor() {

    // One scratch buffer per compositor slot, holding that slot's pixels in the
    // byte order the bitmap reads. Per slot rather than one shared, for the
    // reason the compositor rotates at all: the frame handed out last is still
    // being drawn from.
    private var buffers: Array<IntArray> = emptyArray()
    private var generation: Int = -1
    private var width: Int = 0
    private var height: Int = 0

    actual fun bitmap(frame: AssSurfaceFrame, frameWidth: Int, frameHeight: Int): ImageBitmap {
        val fresh: Boolean = adopt(frame.generation, frameWidth, frameHeight)
        val swapped: IntArray = buffers[frame.slot]

        // A buffer this surface has never seen has to be filled entirely. The
        // compositor's changed region describes ITS buffer's history, not this
        // one's, and trusting it here leaves the rest of the frame unwritten.
        if (fresh) {
            for (row in 0 until frameHeight) swap(frame.pixels, swapped, row, 0, frameWidth - 1, frameWidth)
        } else if (!frame.changed.isEmpty) {
            for (row in frame.changed.top..frame.changed.bottom) {
                swap(frame.pixels, swapped, row, frame.changed.leftAt(row), frame.changed.rightAt(row), frameWidth)
            }
        }

        val bitmap: Bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(swapped))
        return bitmap.asImageBitmap()
    }

    // True when the buffers were replaced, so the caller knows its region is
    // meaningless against them.
    private fun adopt(frameGeneration: Int, frameWidth: Int, frameHeight: Int): Boolean {
        if (buffers.isNotEmpty() && frameGeneration == generation && frameWidth == width && frameHeight == height) {
            return false
        }

        generation = frameGeneration
        width = frameWidth
        height = frameHeight
        buffers = Array(BUFFER_COUNT) { IntArray(frameWidth * frameHeight) }
        return true
    }

    private fun swap(source: IntArray, target: IntArray, row: Int, from: Int, to: Int, stride: Int) {
        if (from > to) return
        val start: Int = row * stride
        for (index in start + from..start + to) {
            val pixel: Int = source[index]
            target[index] = (pixel and ALPHA_GREEN) or
                ((pixel and BLUE) shl RED_SHIFT) or
                ((pixel ushr RED_SHIFT) and BLUE)
        }
    }

    // Nothing to free, and that is a property of this platform rather than an
    // omission. An android.graphics.Bitmap is an ordinary Java object whose
    // pixels have counted against the heap since API 26, so the collector sees
    // the cost and runs for it. Skia's do not, which is why the desktop keeps a
    // ledger and this does not.
    actual fun painted(painted: Int): Unit = Unit
}

// Matches the compositor's own rotation, so a slot index always has a buffer.
private const val BUFFER_COUNT = 2
private const val ALPHA_GREEN = 0xFF00FF00.toInt()
private const val BLUE = 0x000000FF
private const val RED_SHIFT = 16
