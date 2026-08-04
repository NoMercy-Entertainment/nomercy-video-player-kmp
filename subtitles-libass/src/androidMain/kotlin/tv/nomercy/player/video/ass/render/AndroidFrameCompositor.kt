// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

import tv.nomercy.player.video.subtitles.AssImage
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import java.nio.ByteBuffer

// Turns libass's coverage bitmaps into one frame a surface can draw.
//
// libass hands back a run of glyphs at a time: an 8-bit coverage mask and a
// colour, positioned in frame coordinates. Something has to lay those over each
// other in order, and doing it per-surface is how two surfaces come to disagree
// about which glyph is on top.
public class AndroidFrameCompositor(
    private val pool: BitmapPool = BitmapPool(),
) {

    private val paint = Paint().apply { isAntiAlias = false }

    // The result is always a software ARGB_8888 bitmap, and that is not a
    // detail. Canvas(bitmap) refuses a hardware bitmap outright, and a hardware
    // bitmap cannot be read back — a frame allocated as one renders black with
    // nothing reported, which is the single most expensive mistake available
    // here because it looks like a subtitle that failed to arrive.
    public fun composite(images: List<AssImage>, frameWidth: Int, frameHeight: Int): Bitmap {
        val frame: Bitmap = pool.obtain(frameWidth.coerceAtLeast(1), frameHeight.coerceAtLeast(1))
        val canvas = Canvas(frame)
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)

        for (image in images) {
            drawGlyphRun(canvas, image)
        }

        return frame
    }

    private fun drawGlyphRun(canvas: Canvas, image: AssImage) {
        if (image.width <= 0 || image.height <= 0) return

        val coverage: Bitmap = coverageBitmap(image) ?: return
        paint.color = opaqueArgbOf(image.colour)
        canvas.drawBitmap(coverage, image.x.toFloat(), image.y.toFloat(), paint)
        coverage.recycle()
    }

    // ALPHA_8, because that is exactly what libass produced and what Paint
    // modulates against: the mask supplies coverage and the paint supplies the
    // colour. Expanding it to four channels here would cost four times the
    // memory to reach the same pixels.
    private fun coverageBitmap(image: AssImage): Bitmap? {
        val mask: Bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ALPHA_8)
        val expected: Int = image.width * image.height
        if (image.pixels.size < expected) return null

        mask.copyPixelsFromBuffer(ByteBuffer.wrap(image.pixels, 0, expected))
        return mask
    }
}

// libass packs a colour as 0xRRGGBBAA and its last byte is INVERSE alpha: zero
// is opaque and 255 is invisible. Reading it as ordinary alpha draws every
// subtitle at exactly the transparency it should not have, which for the common
// fully-opaque case means drawing nothing at all.
internal fun opaqueArgbOf(libassColour: Int): Int {
    val red: Int = (libassColour ushr 24) and BYTE_MASK
    val green: Int = (libassColour ushr 16) and BYTE_MASK
    val blue: Int = (libassColour ushr 8) and BYTE_MASK
    val alpha: Int = BYTE_MASK - (libassColour and BYTE_MASK)

    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

private const val BYTE_MASK = 0xFF
