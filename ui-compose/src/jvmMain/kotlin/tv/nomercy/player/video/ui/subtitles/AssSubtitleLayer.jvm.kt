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

// BGRA on purpose. Skia's byte order for this type is the little-endian
// reading of an ARGB int, so the bytes go out blue first; writing them in the
// order the int is spelled swaps red and blue, and every subtitle comes out in
// the wrong colour with nothing failing.
internal actual fun assImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val bytes = ByteArray(pixels.size * BYTES_PER_PIXEL)
    var offset = 0
    for (pixel in pixels) {
        bytes[offset++] = pixel.toByte()
        bytes[offset++] = (pixel ushr GREEN_SHIFT).toByte()
        bytes[offset++] = (pixel ushr RED_SHIFT).toByte()
        bytes[offset++] = (pixel ushr ALPHA_SHIFT).toByte()
    }

    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    val bitmap = Bitmap()
    bitmap.installPixels(info, bytes, width * BYTES_PER_PIXEL)

    return bitmap.asComposeImageBitmap()
}

private const val BYTES_PER_PIXEL = 4
private const val GREEN_SHIFT = 8
private const val RED_SHIFT = 16
private const val ALPHA_SHIFT = 24
