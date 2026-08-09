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

    // A FRESH bitmap per frame, deliberately, and it is not an oversight that it
    // is not pooled the way the byte buffers beside it are.
    //
    // Pooling one per compositor slot was tried, on the reasoning that a slot
    // only comes back round every other frame and that this is the same
    // guarantee the byte buffers rely on. It is not the same guarantee. A byte
    // buffer rewritten under a reader yields a torn image; a Skia bitmap whose
    // pixels are reinstalled while the composition is inside
    // `Image.makeFromBitmap` on it is a use-after-free, and it took the whole
    // process down through skiko with the subtitle simply gone in the frames
    // before it. `asComposeImageBitmap` WRAPS, so Compose holds this object for
    // as long as it likes and tells us nothing about when it is done.
    //
    // The cost is real and is not being denied: this is a native allocation per
    // displayed frame and it is where a large part of the desktop's memory
    // goes. That is a separate problem, and it does not get solved by handing
    // the compositor a bitmap somebody else is reading.
    private var generation: Int = -1
    private var width: Int = 0
    private var height: Int = 0

    // Published pictures the canvas has not reported painting past, oldest
    // first. Two threads touch this — the rasteriser publishes and the draw
    // phase drains — and unsynchronised it threw out of the iterator into a
    // thread nobody watches, which is a leak whose only symptom is the picture
    // looking perfect while memory climbs.
    private val held: MutableList<Pair<Int, Bitmap>> = mutableListOf()
    private var published: Int = 0

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
        val bitmap = Bitmap()
        bitmap.installPixels(info, bytes, frameWidth * BYTES_PER_PIXEL)

        // Immutable before it is published, which is what lets the draw SHARE
        // these pixels instead of copying them: Skia's makeFromBitmap copies a
        // mutable bitmap, putting a full-frame allocation back on the render
        // thread. SkiaFrameSink beside this does the same for the picture.
        bitmap.setImmutable()

        // Held until the canvas says it has drawn past it. installPixels copies
        // into a native pixel ref that the JVM heap does not account for, so
        // nothing here is collected on pressure — the wrapper is a few dozen
        // bytes and the pixels are megabytes.
        published += 1
        synchronized(held) { held += published to bitmap }

        return bitmap.asComposeImageBitmap()
    }

    actual fun painted(painted: Int) {
        // Strictly older, never the one named. [painted] is the picture the
        // canvas has just drawn and is still holding; freeing it here would be
        // the same read of freed memory this ledger exists to prevent, only
        // arriving one frame later and therefore harder to attribute.
        //
        // Copied out under the lock and closed outside it: close() is a native
        // call, and holding the lock across one blocks the rasteriser on the
        // composition thread.
        val freed: List<Bitmap> = synchronized(held) {
            val done: List<Bitmap> = held.filter { (version, _) -> version < painted }.map { it.second }
            held.removeAll { (version, _) -> version < painted }
            done
        }

        freed.forEach { bitmap -> bitmap.close() }
    }

    // How many pictures this surface is still holding native memory for.
    //
    // Read from the ledger rather than counted separately: the list IS the set
    // of undead bitmaps, so a count beside it could disagree with it and the
    // disagreement would read as the leak being fixed.
    internal fun liveBitmaps(): Int = synchronized(held) { held.size }

    // True when the buffers were just replaced, which is a resize or the first
    // frame.
    private fun adopt(compositorGeneration: Int, frameWidth: Int, frameHeight: Int): Boolean {
        if (compositorGeneration == generation && sized(frameWidth, frameHeight)) return false

        generation = compositorGeneration
        width = frameWidth
        height = frameHeight
        buffers = Array(BUFFER_COUNT) { ByteArray(frameWidth * frameHeight * BYTES_PER_PIXEL) }
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
