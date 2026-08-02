// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    /**
     * `.top-row`'s inset, which is not the control row's.
     *
     * | container | strip | row |
     * |-----------|-------|-----|
     * | base      | 24    | 16  |
     * | ≤ 720     | 12    | 8   |
     * | ≤ 480     | 8     | 4   |
     * | ≤ 360     | 4     | 2   |
     *
     * The strip is inset further than the controls at every width, so a bar that
     * reuses [paddingHorizontal] for both runs the progress strip out past the
     * play button on either side.
     */
    val stripPaddingHorizontal: Dp = BASE_STRIP_PADDING,
    /**
     * `.time`'s size, which drops one step and then stops.
     *
     * 0.82rem down to 0.75rem below 720 and no further — the two narrower bands
     * reduce the padding around the clocks rather than the clocks. Written out
     * because the browser's root is sixteen pixels: 0.82rem is 13.12, not 13.
     */
    val timeFontSize: TextUnit = BASE_TIME_SIZE,
    /**
     * Whether the right-hand clock is drawn.
     *
     * `@container (max-width: 480px) { .remaining-time { display: none } }`.
     * Below that width the elapsed time stays and the remaining time goes, which
     * is the browser choosing the reading a viewer needs over the one they can
     * work out.
     *
     * A band rule rather than a control rule: the clocks are not buttons and the
     * responsive filter never sees them, so a bar drew both on a phone while the
     * browser drew one — measured at a 360px container, where the web's row is
     * playback, volume, elapsed, divider and nothing else.
     */
    val showsRemaining: Boolean = true,
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
    widthDp <= XS_MAX -> BarMetrics(
        gap = 0.dp,
        paddingVertical = 2.dp,
        paddingHorizontal = 2.dp,
        stripPaddingHorizontal = 4.dp,
        timeFontSize = NARROW_TIME_SIZE,
        showsRemaining = false,
    )

    widthDp <= SM_MAX -> BarMetrics(
        gap = 0.dp,
        paddingVertical = 4.dp,
        paddingHorizontal = 4.dp,
        stripPaddingHorizontal = 8.dp,
        timeFontSize = NARROW_TIME_SIZE,
        showsRemaining = false,
    )

    widthDp <= MD_MAX -> BarMetrics(
        gap = 0.dp,
        paddingVertical = 4.dp,
        paddingHorizontal = 8.dp,
        stripPaddingHorizontal = 12.dp,
        timeFontSize = NARROW_TIME_SIZE,
    )

    else -> BarMetrics(
        gap = 2.dp,
        paddingVertical = 4.dp,
        paddingHorizontal = 16.dp,
        stripPaddingHorizontal = BASE_STRIP_PADDING,
        timeFontSize = BASE_TIME_SIZE,
    )
}

internal const val XS_MAX = 360
internal const val SM_MAX = 480
internal const val MD_MAX = 720

// The browser's root is sixteen pixels, so the arithmetic is written out rather
// than the answer: 0.82rem is 13.12 and 0.75rem is 12.
private val BASE_STRIP_PADDING: Dp = 24.dp
private val BASE_TIME_SIZE: TextUnit = 13.12.sp
private val NARROW_TIME_SIZE: TextUnit = 12.sp
