// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which volume control the chrome offers.
 *
 * `volumeSlider` on DesktopUiOptions. The web defaults to [Auto] deliberately:
 * expand-on-hover needs a real pointer, so it cannot be what a touch device gets.
 */
public enum class VolumeSliderMode {
    /** Vertical when there is no hover or the player is narrow, horizontal otherwise. */
    Auto,

    /** The track that grows out of the button when a pointer rests on it. */
    Horizontal,

    /** A card above the button, opened by pressing it. */
    Vertical,
}

/**
 * Everything the volume control needs that is not the player's state.
 *
 * One value because these travel together through the bar and into two different
 * sliders, and passed one at a time they push both functions past what one is
 * allowed to take. [playerWidthDp] is the PLAYER's width rather than the control's,
 * because that is what the container queries key on.
 */
public data class VolumeSpec(
    val label: String,
    val mode: VolumeSliderMode = VolumeSliderMode.Auto,
    val playerWidthDp: Int = Int.MAX_VALUE,
    val hasHover: Boolean = true,
) {
    /** `state.isNoHover || width <= AUTO_VERTICAL_THRESHOLD`. */
    internal val vertical: Boolean
        get() = when (mode) {
            VolumeSliderMode.Vertical -> true
            VolumeSliderMode.Horizontal -> false
            VolumeSliderMode.Auto -> !hasHover || playerWidthDp <= AUTO_VERTICAL_THRESHOLD
        }
}

/**
 * How wide the track opens to, which narrows with the player.
 *
 * `.volume-slider` gets 80px, then 60, 48 and 32 as the container crosses 720,
 * 480 and 360 — the same breakpoints the bar's spacing uses. A track that stayed
 * 80px wide on a narrow player pushes the clocks off the end of the row.
 */
internal fun expandedWidthFor(widthDp: Int): Dp = when {
    widthDp <= XS_MAX -> 32.dp
    widthDp <= SM_MAX -> 48.dp
    widthDp <= MD_MAX -> 60.dp
    else -> 80.dp
}

// `AUTO_VERTICAL_THRESHOLD` in responsive.ts.
private const val AUTO_VERTICAL_THRESHOLD = 520
