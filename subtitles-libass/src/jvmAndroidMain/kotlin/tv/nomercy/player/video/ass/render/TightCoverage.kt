// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

import tv.nomercy.player.video.subtitles.AssImage

// Packs a libass coverage mask into rows exactly `width` bytes wide.
//
// libass pads every row to `stride`, and the last row stops at the glyph's width
// (stride * (height - 1) + width bytes in all). A bitmap reads `width` bytes per
// row, so copying the buffer straight across shifts every row after the first.
internal fun tightCoverage(image: AssImage): ByteArray? {
    val width: Int = image.width
    val height: Int = image.height
    val stride: Int = image.stride
    if (width <= 0 || height <= 0 || stride < width) return null
    if (image.pixels.size < stride * (height - 1) + width) return null

    val tight = ByteArray(width * height)
    for (row in 0 until height) {
        image.pixels.copyInto(tight, row * width, row * stride, row * stride + width)
    }
    return tight
}
