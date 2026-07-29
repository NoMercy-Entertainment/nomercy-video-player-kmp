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
 * How tightly the control row is packed, which is not one answer.
 *
 * The chrome had a single gap and a single padding. The web has four sets, and
 * they are `@container` queries on the PLAYER's own width rather than media
 * queries on the viewport — so a player in a sidebar tightens while the window
 * stays wide:
 *
 * | container | gap | padding |
 * |-----------|-----|---------|
 * | base      | 2   | 4 / 16  |
 * | ≤ 720     | 0   | 4 / 8   |
 * | ≤ 480     | 0   | 4 / 4   |
 * | ≤ 360     | 0   | 2 / 2   |
 *
 * Found by exporting the running player's computed style at two widths and
 * getting two different answers. Reading the base rule gives 2 and 4/16 and
 * looks like the whole truth, which is the same trap as reading a base
 * declaration for the title size: a CSS property with container queries has
 * more than one value, and the base one is the easy case.
 *
 * A narrow bar with a wide bar's padding loses 24dp of room for controls, which
 * on a phone is a control.
 */
public data class BarMetrics(
    val gap: Dp,
    val paddingVertical: Dp,
    val paddingHorizontal: Dp,
)

/**
 * The metrics for a container of [widthDp].
 *
 * Thresholds are the same numbers as [CHROME_BREAKPOINTS] because they are the
 * same breakpoints in the same stylesheet — but they are read separately on
 * purpose. Those decide WHICH controls survive; these decide how the survivors
 * are spaced, and one list serving both would tie a spacing change to a
 * visibility change.
 */
public fun barMetricsFor(widthDp: Int): BarMetrics = when {
    widthDp <= XS_MAX -> BarMetrics(gap = 0.dp, paddingVertical = 2.dp, paddingHorizontal = 2.dp)
    widthDp <= SM_MAX -> BarMetrics(gap = 0.dp, paddingVertical = 4.dp, paddingHorizontal = 4.dp)
    widthDp <= MD_MAX -> BarMetrics(gap = 0.dp, paddingVertical = 4.dp, paddingHorizontal = 8.dp)
    else -> BarMetrics(gap = 2.dp, paddingVertical = 4.dp, paddingHorizontal = 16.dp)
}

private const val XS_MAX = 360
private const val SM_MAX = 480
private const val MD_MAX = 720
