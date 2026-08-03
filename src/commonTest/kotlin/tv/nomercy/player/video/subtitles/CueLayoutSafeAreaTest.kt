// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import tv.nomercy.player.core.events.ALIGN_CENTER
import tv.nomercy.player.core.events.ALIGN_END
import tv.nomercy.player.core.events.ALIGN_START
import tv.nomercy.player.core.events.SubtitleCue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The caption safe area, as a rule rather than as two constants.
//
// FCC 47 CFR 79.4 requires captions to be viewable IN THEIR ENTIRETY, which is a
// claim about every cue a stream can produce and not about the two numbers the
// overlay is configured with. The layout had thorough tests for where a cue goes
// and none for where it may not go, so a change that let one rung of the
// arithmetic escape the box would have been caught by nothing.
//
// BipBop is the stream this is checked against on the web: Apple's test asset
// carries cues with explicit line and position values rather than the defaults
// every other fixture here uses, which is exactly the input that escapes a
// layout written only for `line:auto`.
class CueLayoutSafeAreaTest {

    private fun cue(line: Double?, position: Double?, size: Double, align: String) = SubtitleCue(
        text = "caption",
        plainText = "caption",
        line = line,
        align = align,
        size = size,
        position = position,
    )

    @Test
    fun theInsetsAreTheActionSafeValuesTheOverlayDeclares() {
        // 5% top and bottom clears any chrome, scrubber or bezel; 3% left and
        // right gives a long line room without crowding. Asserted because they
        // are the standard rather than a preference — a later tidy that rounded
        // them to a single figure would be a caption clipped on somebody's TV.
        assertEquals(5.0, SAFE_AREA_INSET_BLOCK_PERCENT)
        assertEquals(3.0, SAFE_AREA_INSET_INLINE_PERCENT)
    }

    @Test
    fun noCueTheFormatCanExpressEverLeavesTheSafeArea() {
        val lines: List<Double?> = listOf(null, 0.0, 1.0, 14.0, 15.0, 50.0, 85.0, 99.0, 100.0)
        val positions: List<Double?> = listOf(null, 0.0, 10.0, 50.0, 90.0, 100.0)
        val sizes: List<Double> = listOf(10.0, 40.0, 80.0, 100.0)
        val aligns: List<String> = listOf(ALIGN_START, ALIGN_CENTER, ALIGN_END)

        // Flattened into one sequence rather than four nested loops, which is
        // both the readable shape and the one detekt allows.
        val every: List<SubtitleCue> = lines.flatMap { line ->
            positions.flatMap { position ->
                sizes.flatMap { size -> aligns.map { align -> cue(line, position, size, align) } }
            }
        }

        every.forEach { subject ->
            val (left: Double, width: Double) = layOutHorizontally(subject)

            assertTrue(
                left >= 0.0 && left + width <= PERCENT_CEILING,
                "a cue escaped the safe area: line=${subject.line} position=${subject.position} " +
                    "size=${subject.size} align=${subject.align} gave left=$left width=$width",
            )
        }
    }

    @Test
    fun anExplicitlyPositionedCueKeepsItsPlaceRatherThanBeingRecentred() {
        // The half of the format BipBop exercises and `line:auto` fixtures do
        // not. A layout that clamped by recentring would pass the containment
        // test above while putting every caption in the middle of the picture.
        val (left: Double, width: Double) =
            layOutHorizontally(cue(line = 10.0, position = 10.0, size = 30.0, align = ALIGN_START))

        assertEquals(10.0, left)
        assertEquals(30.0, width)
    }

    @Test
    fun aCueAnchoredAtTheBottomEdgeAnchorsFromTheBottom() {
        // line:100 anchored by its top edge would put the whole box below the
        // picture, which is the failure the standard is about.
        assertEquals(CueAnchor.Bottom, anchorFor(100.0))
        assertEquals(CueAnchor.Top, anchorFor(0.0))
    }
}

private const val PERCENT_CEILING = 100.0
