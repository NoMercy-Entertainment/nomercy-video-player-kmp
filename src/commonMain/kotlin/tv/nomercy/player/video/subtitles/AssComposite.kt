// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

// libass packs a colour as 0xRRGGBBAA and its last byte is INVERSE alpha: zero
// is opaque and 255 is invisible. Reading it as ordinary alpha draws every
// subtitle at exactly the transparency it should not have, which for the common
// fully-opaque case means drawing nothing at all.
public fun assArgbOf(libassColour: Int): Int {
    val red: Int = (libassColour ushr RED_BYTE) and BYTE_MASK
    val green: Int = (libassColour ushr GREEN_BYTE) and BYTE_MASK
    val blue: Int = (libassColour ushr BLUE_BYTE) and BYTE_MASK
    val alpha: Int = BYTE_MASK - (libassColour and BYTE_MASK)

    return (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

/**
 * Lays libass's glyph runs over each other into one frame of ARGB pixels.
 *
 * libass hands back a run of glyphs at a time — an 8-bit coverage mask, a
 * colour, and a position in frame coordinates — and something has to lay those
 * over each other in order. Doing it per-surface is how two surfaces come to
 * disagree about which glyph is on top; an outline drawn over the glyph it
 * outlines is the visible version of that.
 *
 * A fresh buffer each call rather than one reused across frames: every surface
 * this feeds hands the pixels to a bitmap that keeps them, and a buffer written
 * again underneath one would repaint a frame that is already on screen.
 */
public fun compositeAssFrame(
    images: List<AssImage>,
    width: Int,
    height: Int,
): IntArray {
    val pixels = IntArray(width * height)

    for (image in images) {
        blit(pixels, width, height, image)
    }

    return pixels
}

private fun blit(pixels: IntArray, width: Int, height: Int, image: AssImage) {
    if (image.width <= 0 || image.height <= 0) return

    // A run whose declared rectangle is larger than the bytes that arrived.
    // Reading past the array here would be an exception per frame rather than
    // a missing glyph.
    if (image.pixels.size < image.stride * image.height) return

    val colour: Int = assArgbOf(image.colour)
    val tintAlpha: Int = (colour ushr ALPHA_SHIFT) and BYTE_MASK
    if (tintAlpha == 0) return

    val tint: Int = colour and RGB_MASK
    for (row in 0 until image.height) {
        blitRow(pixels, width, height, image, row, tint, tintAlpha)
    }
}

@Suppress("LongParameterList")
private fun blitRow(
    pixels: IntArray,
    width: Int,
    height: Int,
    image: AssImage,
    row: Int,
    tint: Int,
    tintAlpha: Int,
) {
    val y: Int = image.y + row
    if (y !in 0 until height) return

    for (column in 0 until image.width) {
        val x: Int = image.x + column
        if (x !in 0 until width) continue

        val coverage: Int = image.pixels[row * image.stride + column].toInt() and BYTE_MASK
        val alpha: Int = coverage * tintAlpha / BYTE_MASK
        if (alpha > 0) {
            val offset: Int = y * width + x
            pixels[offset] = sourceOver(pixels[offset], tint, alpha)
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
    val green: Int =
        blend(sourceRgb ushr GREEN_SHIFT, destination ushr GREEN_SHIFT, sourceAlpha, destinationAlpha, outAlpha)
    val blue: Int = blend(sourceRgb, destination, sourceAlpha, destinationAlpha, outAlpha)

    return (outAlpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

@Suppress("LongParameterList")
private fun blend(source: Int, destination: Int, sourceAlpha: Int, destinationAlpha: Int, outAlpha: Int): Int {
    val weighted: Int = (source and BYTE_MASK) * sourceAlpha +
        (destination and BYTE_MASK) * destinationAlpha * (BYTE_MASK - sourceAlpha) / BYTE_MASK

    return (weighted / outAlpha).coerceIn(0, BYTE_MASK)
}

private const val BYTE_MASK = 0xFF
private const val RGB_MASK = 0xFFFFFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val RED_BYTE = 24
private const val GREEN_BYTE = 16
private const val BLUE_BYTE = 8
