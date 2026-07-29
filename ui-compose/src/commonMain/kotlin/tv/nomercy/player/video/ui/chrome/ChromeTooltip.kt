// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

/**
 * Where a control's tooltip sits, and when it appears.
 *
 * Ported from desktop-ui/helpers/tooltips.ts, which was the one block of that
 * plugin with nothing on this side answering it at all — so a pointer resting
 * on a button here said nothing while the same button on the web named itself.
 *
 * The rules are the web's, not new ones:
 *
 *  - a tooltip appears after half a second of hovering, not instantly. A label
 *    that follows a pointer across a row of nineteen buttons is a strobe.
 *  - it hides on leave AND on click, because a viewer who has pressed the
 *    button has their answer and does not need it named afterwards.
 *  - it is centred on its button and clamped inside the bar, so the first and
 *    last controls do not push a label off the edge of the picture.
 *
 * The geometry is here rather than in a composable because it is arithmetic and
 * that is worth testing without a window: the whole reason the web's version
 * has an `--arrow-x` is that the label moves while the arrow must keep pointing
 * at the button, and getting that backwards is invisible until the bar is
 * narrow.
 */
public object ChromeTooltip {

    /** The web's 500ms hover delay. */
    public const val DELAY_MS: Long = 500

    /**
     * Where to draw a tooltip of [tooltipWidth] centred on a button at
     * [buttonCenter], kept inside [boundsLeft]..[boundsRight].
     *
     * Returns the tooltip's left edge. Everything is in the same unit the
     * caller measures in — the web works in CSS pixels and Compose works in
     * pixels, and the rule is the same arithmetic either way.
     */
    public fun leftFor(
        buttonCenter: Float,
        tooltipWidth: Float,
        boundsLeft: Float,
        boundsRight: Float,
    ): Float {
        val half: Float = tooltipWidth / 2f
        val rawLeft: Float = buttonCenter - half
        val rawRight: Float = buttonCenter + half

        // The web's shape, kept: clamp both edges, then decide which one moved.
        // Written as a single coerceIn it reads the same and is not — a tooltip
        // wider than the bar has to pick an edge to hang off, and this picks
        // the left one, as the web does.
        val clampedLeft: Float = maxOf(boundsLeft, rawLeft)
        val clampedRight: Float = minOf(boundsRight, rawRight)

        return when {
            rawLeft < clampedLeft -> clampedLeft
            rawRight > clampedRight -> clampedRight - tooltipWidth
            else -> rawLeft
        }
    }

    /**
     * How far the label moved from centred, so an arrow can point back at the
     * button it belongs to.
     *
     * Zero when nothing was clamped. The web writes this into `--arrow-x` and a
     * chrome that drew the arrow at a fixed centre would leave it pointing at
     * whatever happens to sit beside the button near the edges of the bar.
     */
    public fun arrowShift(
        buttonCenter: Float,
        tooltipWidth: Float,
        boundsLeft: Float,
        boundsRight: Float,
    ): Float {
        val left: Float = leftFor(buttonCenter, tooltipWidth, boundsLeft, boundsRight)
        return left - buttonCenter + tooltipWidth / 2f
    }
}
