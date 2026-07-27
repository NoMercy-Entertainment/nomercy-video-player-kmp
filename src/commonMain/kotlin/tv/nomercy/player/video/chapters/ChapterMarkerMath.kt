// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.chapters

import tv.nomercy.player.core.media.Chapter

// Where each chapter's segment sits along the bar.
//
// A marker's right edge is the NEXT chapter's left edge, and the last one runs
// to the end of the item. The web player reads an end off the chapter itself,
// and that is the one part of it that cannot be ported: a Chapter here carries a
// start and no end, precisely so that two adjacent chapters cannot disagree
// about where the boundary between them is.
public fun chapterMarkers(chapters: List<Chapter>, duration: Double): List<ChapterMarker> {
    // Nothing to divide by yet. Every marker would land at zero width on top of
    // every other one, which draws as a single dark line across the bar.
    if (!duration.isAKnownLength() || chapters.isEmpty()) return emptyList()

    val ordered: List<Chapter> = chapters.sortedBy { it.startTime }

    return ordered.mapIndexed { index, chapter ->
        val endsAt: Double = ordered.getOrNull(index + 1)?.startTime ?: duration

        ChapterMarker(
            index = index,
            leftPercent = chapter.startTime / duration * PERCENT,
            rightPercent = endsAt / duration * PERCENT,
        )
    }
}

// How much of one segment is behind a given point on the bar, from nothing to
// all of it. The same answer drives progress, buffer and hover, which is why it
// takes the point rather than a playback position.
public fun chapterFill(marker: ChapterMarker, valuePercent: Double): Double {
    // A scan that finds a one-frame segment produces two chapters starting on
    // the same second, and that marker is zero-width. It leaves through the
    // first branch, which is also why there is no minimum-span guard below:
    // reaching the division means the right edge is strictly past the value and
    // the value strictly past the left, so the width cannot be zero and the
    // ratio cannot leave nought-to-one.
    //
    // The web player carries such a guard and it is unreachable there for the
    // same reason. Porting it kept a very narrow chapter from filling correctly
    // — a segment four ten-thousandths of a percent wide read as a tenth full
    // where it should have read a quarter.
    if (valuePercent <= marker.leftPercent) return 0.0
    if (valuePercent >= marker.rightPercent) return 1.0

    return (valuePercent - marker.leftPercent) / (marker.rightPercent - marker.leftPercent)
}

// Zero before a container is read, negative from libVLC for "not yet", and
// infinite on a live stream. None of the three is a length to divide by.
private fun Double.isAKnownLength(): Boolean = isFinite() && this > 0.0

private const val PERCENT = 100.0
