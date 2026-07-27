// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.chapters

// One segment of a chaptered scrubber, as a share of the bar.
//
// Percentages rather than pixels because the three chromes that draw this lay it
// out differently — a Compose bar filling its width, a SwiftUI one inside a safe
// area, and a web one in a flex row — and the only thing they agree on is where
// a chapter sits proportionally.
public data class ChapterMarker(
    val index: Int,
    val leftPercent: Double,
    val rightPercent: Double,
)
