// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlin.math.max
import kotlin.math.min

// Where a subtitle cue goes, as percentages of the safe area.
//
// Ported from desktop-ui's sibling, the `subtitle-overlay` plugin. This is the
// half a browser does for free and nothing native does at all: a sidecar .vtt
// on Android or Apple has a parsed cue and nowhere to put it.
//
// Deliberately geometry only. No Compose, no SwiftUI, no view: the same numbers
// drive both chromes and can be checked without either. The renderer is a box
// at these coordinates with this text in it.
//
// Note this is NOT the libass path. ASS subtitles carry their own positioning
// and are drawn by the subtitles-libass module; this is WebVTT and SRT, where
// the format specifies a line, an alignment and a size, and the player is
// expected to lay them out.

/** How the text sits inside its own box. W3C WebVTT `align`. */
public enum class CueAlign { Start, Center, End }

/** Which edge of the box the [CueBox.linePercent] anchor refers to. */
public enum class CueAnchor { Top, Bottom }

/** One cue as the format describes it, before any layout. */
public data class SubtitleCue(
    val text: String,
    /**
     * Vertical anchor, 0 at the top of the safe area and 100 at the bottom.
     * Null is the format's `line:auto`, which stacks up from the bottom.
     */
    val linePercent: Double? = null,
    val align: CueAlign = CueAlign.Center,
    /** Horizontal anchor. Null takes the default implied by [align]. */
    val positionPercent: Double? = null,
    /** Box width as a percentage of the safe area. */
    val sizePercent: Double = FULL_WIDTH,
)

/** One laid-out box, in safe-area percentages. */
public data class CueBox(
    val text: String,
    val leftPercent: Double,
    val widthPercent: Double,
    val linePercent: Double,
    val anchor: CueAnchor,
    val align: CueAlign,
)

/**
 * The horizontal box, from `size`, `align` and `position`.
 *
 * W3C WebVTT 1.0: the anchor is 0 for start, 0.5 for centre and 1 for end;
 * position defaults to the same edge the alignment names; left is
 * `position - anchor * size`.
 *
 * Where the spec says to abandon the explicit layout and retry as `line:auto`
 * if the box would run past the safe area, this clamps the width instead. That
 * is what the web does, and it is the better answer for a viewer: the requested
 * anchor is preserved and no trailing text is lost off the edge.
 */
public fun layOutHorizontally(cue: SubtitleCue): Pair<Double, Double> {
    val size: Double = cue.sizePercent.coerceIn(0.0, FULL_WIDTH)

    val anchor: Double
    val positionDefault: Double
    when (cue.align) {
        CueAlign.Start -> { anchor = 0.0; positionDefault = 0.0 }
        CueAlign.End -> { anchor = 1.0; positionDefault = FULL_WIDTH }
        CueAlign.Center -> { anchor = CENTRE_ANCHOR; positionDefault = MIDPOINT }
    }

    val position: Double = cue.positionPercent ?: positionDefault
    val left: Double = (position - anchor * size).coerceIn(0.0, FULL_WIDTH)
    val width: Double = size.coerceIn(0.0, FULL_WIDTH - left)

    return left to width
}

/**
 * Which edge the line anchors to.
 *
 * A native VTT renderer defaults `lineAlign` to `start`, so the box's TOP edge
 * sits at `line%` — and then falls back to the other edge when that would push
 * the box out of the safe area. This approximates the fallback by anchoring on
 * the nearer edge: at or below the midpoint the top, above it the bottom.
 *
 * The effect is what a viewer expects at the extremes. `line:0` sits on the top
 * edge and `line:100` on the bottom, where anchoring everything by the top
 * would push a `line:100` cue entirely off the picture.
 */
public fun anchorFor(linePercent: Double): CueAnchor =
    if (linePercent > MIDPOINT) CueAnchor.Bottom else CueAnchor.Top

/**
 * Every active cue, laid out and pushed apart so none covers another.
 *
 * The separation is the part that is easy to leave out and impossible to miss
 * once it bites: two cues at `line:14` and `line:15` paint on top of each
 * other, and the result is not two subtitles slightly misaligned but one
 * illegible smear.
 *
 * [cueHeightPercent] is how tall a cue box is as a percentage of the safe area,
 * which the caller measures — text length and font size decide it, and this
 * file draws nothing.
 *
 * Later cues move DOWN when there is room below and UP when there is not, which
 * is the web's order of preference. Moving up as the first choice would walk
 * cues off the top of a picture whose subtitles all sit near the bottom, which
 * is where subtitles usually sit.
 */
public fun layOutCues(
    cues: List<SubtitleCue>,
    cueHeightPercent: Double,
    defaultLinePercent: Double = DEFAULT_LINE_PERCENT,
): List<CueBox> {
    val placed = mutableListOf<CueBox>()
    // Top edge of each box already placed, so overlap is one comparison.
    val takenTops = mutableListOf<Double>()

    cues.forEach { cue ->
        val (left, width) = layOutHorizontally(cue)
        val requested: Double = cue.linePercent ?: defaultLinePercent
        val anchor: CueAnchor = anchorFor(requested)

        // Everything is compared as a top edge, whichever edge it anchors to,
        // because two boxes overlap by their extents and not by their anchors.
        val requestedTop: Double = when (anchor) {
            CueAnchor.Top -> requested
            CueAnchor.Bottom -> FULL_WIDTH - requested - cueHeightPercent
        }

        val top: Double = freeSlot(requestedTop, takenTops, cueHeightPercent)
        takenTops += top

        placed += CueBox(
            text = cue.text,
            leftPercent = left,
            widthPercent = width,
            linePercent = when (anchor) {
                CueAnchor.Top -> top
                CueAnchor.Bottom -> FULL_WIDTH - top - cueHeightPercent
            },
            anchor = anchor,
            align = cue.align,
        )
    }

    return placed
}

// Down first, then up, then wherever it was asked for.
//
// The last case is deliberate rather than a give-up: a safe area too small to
// hold the cues without overlap should still show them, and refusing to place
// one would drop a line of dialogue entirely.
private fun freeSlot(
    requestedTop: Double,
    taken: List<Double>,
    height: Double,
): Double {
    val clashing: List<Double> = taken.filter { overlaps(requestedTop, it, height) }
    if (clashing.isEmpty()) return requestedTop

    val below: Double = clashing.max() + height
    val above: Double = clashing.min() - height

    return when {
        below + height <= FULL_WIDTH -> below
        above >= 0.0 -> above
        // Neither fits. Placing it where it was asked for keeps the line on
        // screen; refusing would drop a line of dialogue entirely.
        else -> requestedTop
    }
}

private fun overlaps(topA: Double, topB: Double, height: Double): Boolean =
    max(topA, topB) < min(topA, topB) + height

/** Percentages, so a full-width box is 100. */
public const val FULL_WIDTH: Double = 100.0

// Centre alignment anchors on the middle of its own box and defaults to the
// middle of the safe area. Named because 0.5 and 50 sitting beside 0 and 100
// read as arbitrary until they are.
private const val CENTRE_ANCHOR: Double = 0.5
private const val MIDPOINT: Double = FULL_WIDTH / 2

/**
 * Where a cue with no line goes.
 *
 * Near the bottom, inside the safe area, which is where a viewer looks for
 * subtitles and where every renderer that has no better instruction puts them.
 */
public const val DEFAULT_LINE_PERCENT: Double = 90.0

/**
 * The safe-area inset, as a percentage of each edge.
 *
 * SMPTE RP 27.3's 5% rule. FCC 47 CFR requires captions to stay inside the
 * viewable area without pinning a number; 5% is the number the broadcast world
 * settled on, and it is the one the web overlay uses. Cue coordinates are
 * relative to the area INSIDE this, not to the picture.
 */
public const val SAFE_AREA_INSET_PERCENT: Double = 5.0
