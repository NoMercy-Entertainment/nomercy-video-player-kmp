// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import tv.nomercy.player.core.ports.DynamicRange
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.video.ui.chrome.ChromeState

// What the quality pane offers and what it calls each row.
//
// Its own file because these are rules rather than layout, and the pane that
// draws them was at the limit of what one file should hold.

/**
 * The rungs worth offering, which is not every rung the manifest declares.
 *
 *     levels = allLevels.filter(l => l.dynamicRange !== 'hdr' || displayHdr)
 *
 * A browser drops HDR renditions an SDR panel cannot render, because it would
 * decode them and map the colours back down — a washed-out picture the viewer
 * chose. Nothing filtered here, so Cosmos Laundromat's `1920x804 PQ` sat in the
 * list on an SDR desktop, and picking it was a way to make the film look worse.
 *
 * [hdrDisplay] is conservative-false where the platform will not say, which is
 * the same answer the browser reaches on a panel it cannot interrogate.
 */
internal fun offerableRungs(levels: List<QualityLevel>, hdrDisplay: Boolean): List<QualityLevel> =
    levels.filter { hdrDisplay || it.dynamicRange == DynamicRange.SDR }

internal fun autoQualitySubLabel(state: ChromeState): String? =
    state.activeQuality.takeIf { state.qualityAuto }?.let { qualityLabel(it) }

internal fun qualityLabel(level: QualityLevel): String {
    // `1280x536 SDR`, which is what the browser puts in the row - both
    // dimensions and the range, always, in capitals.
    //
    // This wrote `${height}p` and hid the range on SDR, so a ladder read
    // "108p / 178p / 268p" beside a browser reading "256x108 SDR / 426x178 SDR
    // / 640x268 SDR". A height alone is not a rung a viewer recognises: 536p is
    // not a number anyone has seen, and it is the same stream the browser calls
    // 1280x536.
    val size: String = level.width?.let { width -> "${width}x${level.height}" } ?: "${level.height}p"

    return "$size ${level.dynamicRange.wire.uppercase()}"
}
