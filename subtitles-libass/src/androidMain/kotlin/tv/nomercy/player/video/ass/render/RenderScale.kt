// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

// How much of the surface a device can afford to rasterize subtitles at.
//
// One factor for both axes, so the overlay keeps the shape of what it is drawn
// onto — scaling the two independently squashes the text, which on a screen at
// an unusual aspect is worse than drawing it small.
//
// Never above one. A subtitle rendered larger than the surface it lands on costs
// memory to produce and detail nobody can see, and on the devices this exists
// for that memory is the whole problem.
//
// A surface that has not been measured is left alone rather than divided by. A
// view is routinely measured on one axis before the other, and the half-measured
// state answers with the known axis clamped against a height of zero — which is
// a scale for a surface that does not exist yet, applied to the one that arrives
// a frame later.
internal fun renderScaleFor(width: Int, height: Int, tier: MemoryTier): Double {
    if (width <= 0 || height <= 0) return 1.0

    val byWidth: Double = tier.maxRenderWidth.toDouble() / width
    val byHeight: Double = tier.maxRenderHeight.toDouble() / height

    return minOf(1.0, byWidth, byHeight)
}
