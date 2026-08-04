// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

import tv.nomercy.player.video.subtitles.AssImage
import java.awt.image.BufferedImage

// Lays libass's glyph runs over each other into one desktop frame.
//
// The same job the Android compositor does and deliberately the same order:
// runs come back back-to-front and a compositor that drew them in any other
// order would put an outline over the glyph it outlines.
//
// Done by hand rather than through Graphics2D. The source is an 8-bit coverage
// plane and the destination is ARGB, and getting AWT to treat one as a mask for
// a flat colour means building a paint and a raster per run — more allocation
// per frame than the blit itself costs.
public class JvmFrameCompositor {

    public fun composite(images: List<AssImage>, frameWidth: Int, frameHeight: Int): BufferedImage {
        val frame = BufferedImage(
            frameWidth.coerceAtLeast(1),
            frameHeight.coerceAtLeast(1),
            BufferedImage.TYPE_INT_ARGB,
        )

        for (image in images) {
            blit(frame, image)
        }

        return frame
    }

    private fun blit(frame: BufferedImage, image: AssImage) {
        if (image.width <= 0 || image.height <= 0) return

        // A run whose declared rectangle is larger than the bytes that arrived.
        // Reading past the array here would be an exception per frame rather
        // than a missing glyph.
        if (image.pixels.size < image.stride * image.height) return

        val tint: Int = rgbOf(image.colour)
        val tintAlpha: Int = alphaOf(image.colour)
        if (tintAlpha == 0) return

        for (row in 0 until image.height) {
            blitRow(frame, image, row, tint, tintAlpha)
        }
    }

    private fun blitRow(frame: BufferedImage, image: AssImage, row: Int, tint: Int, tintAlpha: Int) {
        val y: Int = image.y + row
        if (y !in 0 until frame.height) return

        for (column in 0 until image.width) {
            val x: Int = image.x + column
            if (x !in 0 until frame.width) continue

            val coverage: Int = image.pixels[row * image.stride + column].toInt() and BYTE_MASK
            if (coverage == 0) continue

            val alpha: Int = coverage * tintAlpha / BYTE_MASK
            if (alpha == 0) continue

            frame.setRGB(x, y, sourceOver(frame.getRGB(x, y), tint, alpha))
        }
    }
}

// Source-over, the one compositing rule ASS needs: a glyph's outline is drawn
// under it and its shadow under that, each partially covering what came before.
internal fun sourceOver(destination: Int, sourceRgb: Int, sourceAlpha: Int): Int {
    val inverse: Int = BYTE_MASK - sourceAlpha
    val destinationAlpha: Int = (destination ushr ALPHA_SHIFT) and BYTE_MASK

    val outAlpha: Int = sourceAlpha + destinationAlpha * inverse / BYTE_MASK
    if (outAlpha == 0) return 0

    val red: Int = blend(sourceRgb ushr RED_SHIFT, destination ushr RED_SHIFT, sourceAlpha, destinationAlpha, outAlpha)
    val green: Int = blend(sourceRgb ushr GREEN_SHIFT, destination ushr GREEN_SHIFT, sourceAlpha, destinationAlpha, outAlpha)
    val blue: Int = blend(sourceRgb, destination, sourceAlpha, destinationAlpha, outAlpha)

    return (outAlpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

@Suppress("LongParameterList")
private fun blend(source: Int, destination: Int, sourceAlpha: Int, destinationAlpha: Int, outAlpha: Int): Int {
    val weighted: Int = (source and BYTE_MASK) * sourceAlpha +
        (destination and BYTE_MASK) * destinationAlpha * (BYTE_MASK - sourceAlpha) / BYTE_MASK

    return (weighted / outAlpha).coerceIn(0, BYTE_MASK)
}

// libass packs 0xRRGGBBAA and its last byte is INVERSE alpha: zero is opaque.
internal fun alphaOf(libassColour: Int): Int = BYTE_MASK - (libassColour and BYTE_MASK)

internal fun rgbOf(libassColour: Int): Int = (libassColour ushr BYTE_BITS) and RGB_MASK

private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8
private const val RGB_MASK = 0xFFFFFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
