// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import tv.nomercy.player.video.subtitles.AssSurfaceFrame

// Skia wants bytes and the compositor holds ints, so the desktop is the one
// surface that has to convert. Doing it over the whole frame cost 13.9ms a
// frame at 1920x1080 — more than libass, the copy out of it and the compositing
// added together — because eight million pixels are converted to find the few
// hundred thousand that changed.
//
// So a byte buffer per compositor buffer, kept, and only the changed band
// rewritten. The compositor says which buffer a frame came from and what part
// of it is new; both are needed, since the same slot comes back round every
// other frame holding what IT was given rather than what was drawn in between.
//
// BGRA is not a choice. Skia's byte order for this type is the little-endian
// reading of an ARGB int, so the bytes go out blue first; writing them in the
// order the int is spelled swaps red and blue, and every subtitle comes out in
// the wrong colour with nothing failing.
//
// PREMUL because the compositor hands over premultiplied pixels. Declaring
// UNPREMUL made Skia multiply them a second time, and the way that shows is a
// subtitle whose antialiased edges are darker than its face.
internal actual class AssPictureSurface actual constructor() {

    private var buffers: Array<ByteArray> = emptyArray()

    // One Skia bitmap per compositor slot, for the same reason there is one
    // byte buffer per slot: a fresh Bitmap() every frame is a NATIVE allocation
    // per frame, and at display rate that is where the desktop's memory went —
    // 1080p subtitles sawtoothing between 0.9 and 2.2 GB while the byte buffers
    // beside them were being reused correctly. Skia's cleaner reclaims them
    // eventually, and eventually is not fast enough at sixty a second.
    //
    // Per SLOT rather than one shared: asComposeImageBitmap WRAPS the bitmap,
    // so the composition may still be painting the previous frame. A slot only
    // comes back round every other frame, which is exactly the guarantee that
    // makes rewriting its pixels safe — the same guarantee the byte buffers
    // already rely on.
    private var bitmaps: Array<Bitmap> = emptyArray()
    private var generation: Int = -1
    private var width: Int = 0
    private var height: Int = 0

    actual fun bitmap(frame: AssSurfaceFrame, frameWidth: Int, frameHeight: Int): ImageBitmap {
        val fresh: Boolean = adopt(frame.generation, frameWidth, frameHeight)
        val bytes: ByteArray = buffers[frame.slot]

        // A buffer this surface has never seen has to be filled entirely; the
        // compositor's changed region describes ITS buffer's history, not this
        // one's, and trusting it here leaves the rest of the frame unwritten.
        if (fresh) {
            for (y in 0 until frameHeight) {
                convert(frame.pixels, bytes, y, 0, frameWidth - 1, frameWidth)
            }
        } else if (!frame.changed.isEmpty) {
            for (y in frame.changed.top..frame.changed.bottom) {
                convert(frame.pixels, bytes, y, frame.changed.leftAt(y), frame.changed.rightAt(y), frameWidth)
            }
        }

        val info = ImageInfo(frameWidth, frameHeight, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bitmap: Bitmap = bitmaps[frame.slot]
        bitmap.installPixels(info, bytes, frameWidth * BYTES_PER_PIXEL)

        return bitmap.asComposeImageBitmap()
    }

    // True when the buffers were just replaced, which is a resize or the first
    // frame.
    private fun adopt(compositorGeneration: Int, frameWidth: Int, frameHeight: Int): Boolean {
        if (compositorGeneration == generation && sized(frameWidth, frameHeight)) return false

        generation = compositorGeneration
        width = frameWidth
        height = frameHeight
        buffers = Array(BUFFER_COUNT) { ByteArray(frameWidth * frameHeight * BYTES_PER_PIXEL) }

        // The old ones go with their buffers. Closed rather than dropped: a
        // Skia bitmap holds native memory that the JVM heap does not account
        // for, so leaving them to the cleaner is what this change is undoing.
        bitmaps.forEach { old -> old.close() }
        bitmaps = Array(BUFFER_COUNT) { Bitmap() }
        return true
    }

    private fun sized(frameWidth: Int, frameHeight: Int): Boolean = frameWidth == width && frameHeight == height

    // Written out by hand rather than through an IntBuffer view. A
    // little-endian int view over a heap byte array is not a bulk copy — the
    // JVM writes it a pixel at a time with the swap, which measured slower than
    // this loop and hid the cost behind an API that reads like a memcpy.
    @Suppress("LongParameterList")
    private fun convert(pixels: IntArray, bytes: ByteArray, row: Int, left: Int, right: Int, stride: Int) {
        if (left > right) return

        var source: Int = row * stride + left
        var target: Int = source * BYTES_PER_PIXEL

        repeat(right - left + 1) {
            val pixel: Int = pixels[source++]
            bytes[target++] = pixel.toByte()
            bytes[target++] = (pixel ushr GREEN_SHIFT).toByte()
            bytes[target++] = (pixel ushr RED_SHIFT).toByte()
            bytes[target++] = (pixel ushr ALPHA_SHIFT).toByte()
        }
    }
}

private const val BUFFER_COUNT = 2
private const val BYTES_PER_PIXEL = 4
private const val GREEN_SHIFT = 8
private const val RED_SHIFT = 16
private const val ALPHA_SHIFT = 24
