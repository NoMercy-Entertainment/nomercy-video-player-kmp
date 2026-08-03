// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * How big the scrub bubble's picture is drawn.
 *
 * A share of the bar, so it scales with the player the way a browser's does,
 * capped at the sheet's own tile so it is never upscaled past the pixels that
 * exist, and floored so it stays readable on a narrow phone.
 *
 * The floor gives way to the cap, and that is the whole of this function. Two
 * rules that were both written as one `coerceIn` disagree the moment a sheet's
 * tile is smaller than the floor — thumbs_160x68 on a 2.84-density phone is
 * 56dp against a 128dp floor — and `coerceIn(128.dp, 56.dp)` does not clamp, it
 * throws. Seeking crashed the app outright on every item whose sheet was cut
 * small.
 */
internal fun previewFrameSize(
    barWidth: Dp,
    share: Float,
    minWidth: Dp,
    tile: DpSize,
): DpSize {
    val floor: Dp = minOf(minWidth, tile.width)
    val width: Dp = (barWidth * share).coerceIn(floor, tile.width)
    val aspect: Float = tile.height / tile.width

    return DpSize(width, width * aspect)
}
