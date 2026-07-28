// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CueLayoutTest {

    private fun cue(
        text: String = "line",
        line: Double? = null,
        align: CueAlign = CueAlign.Center,
        position: Double? = null,
        size: Double = 100.0,
    ) = SubtitleCue(text, line, align, position, size)

    // W3C WebVTT: left = position - anchor * size, and position defaults to the
    // edge the alignment names.
    @Test
    fun aCentredFullWidthCueFillsTheSafeArea() {
        assertEquals(0.0 to 100.0, layOutHorizontally(cue()))
    }

    @Test
    fun eachAlignmentTakesItsOwnDefaultPosition() {
        assertEquals(0.0 to 40.0, layOutHorizontally(cue(align = CueAlign.Start, size = 40.0)))
        assertEquals(30.0 to 40.0, layOutHorizontally(cue(align = CueAlign.Center, size = 40.0)))
        assertEquals(60.0 to 40.0, layOutHorizontally(cue(align = CueAlign.End, size = 40.0)))
    }

    @Test
    fun anExplicitPositionOverridesTheDefault() {
        assertEquals(10.0 to 40.0, layOutHorizontally(cue(align = CueAlign.Start, position = 10.0, size = 40.0)))
    }

    // The spec says to abandon the layout and retry as line:auto when the box
    // runs past the edge. The web clamps the width instead, which keeps the
    // requested anchor and never loses trailing text off-screen.
    @Test
    fun aBoxRunningPastTheEdgeIsClampedRatherThanAbandoned() {
        val (left, width) = layOutHorizontally(
            cue(align = CueAlign.Start, position = 80.0, size = 40.0),
        )

        assertEquals(80.0, left)
        assertEquals(20.0, width)
        assertTrue(left + width <= 100.0)
    }

    @Test
    fun anAbsurdSizeIsClampedToTheSafeArea() {
        val (left, width) = layOutHorizontally(cue(size = 500.0))

        assertEquals(0.0, left)
        assertEquals(100.0, width)
    }

    // Anchoring everything by the top would push a line:100 cue entirely off
    // the picture.
    @Test
    fun theLineAnchorsToTheNearerEdge() {
        assertEquals(CueAnchor.Top, anchorFor(0.0))
        assertEquals(CueAnchor.Top, anchorFor(50.0))
        assertEquals(CueAnchor.Bottom, anchorFor(50.1))
        assertEquals(CueAnchor.Bottom, anchorFor(100.0))
    }

    @Test
    fun aCueWithNoLineGoesNearTheBottom() {
        val box = layOutCues(listOf(cue()), cueHeightPercent = 8.0).single()

        assertEquals(CueAnchor.Bottom, box.anchor)
        assertEquals(DEFAULT_LINE_PERCENT, box.linePercent)
    }

    // The rule that is easy to leave out and impossible to miss once it bites:
    // two cues a percent apart paint one illegible smear, not two subtitles.
    @Test
    fun overlappingCuesArePushedApart() {
        val boxes = layOutCues(
            listOf(cue("first", line = 14.0), cue("second", line = 15.0)),
            cueHeightPercent = 8.0,
        )

        assertEquals(2, boxes.size)
        val tops = boxes.map { it.linePercent }
        assertTrue(
            kotlin.math.abs(tops[0] - tops[1]) >= 8.0,
            "the two cues still overlap: $tops",
        )
    }

    @Test
    fun cuesThatDoNotOverlapAreLeftWhereTheyWereAsked() {
        val boxes = layOutCues(
            listOf(cue("top", line = 10.0), cue("lower", line = 40.0)),
            cueHeightPercent = 8.0,
        )

        assertEquals(10.0, boxes[0].linePercent)
        assertEquals(40.0, boxes[1].linePercent)
    }

    // Down before up. Moving up first would walk cues off the top of a picture
    // whose subtitles all sit near the bottom, which is where they usually sit.
    @Test
    fun aDisplacedCueMovesDownWhenThereIsRoom() {
        val boxes = layOutCues(
            listOf(cue("first", line = 20.0), cue("second", line = 20.0)),
            cueHeightPercent = 8.0,
        )

        assertEquals(20.0, boxes[0].linePercent)
        assertEquals(28.0, boxes[1].linePercent)
    }

    // A safe area too small to separate them should still show both. Refusing
    // to place one would drop a line of dialogue entirely.
    @Test
    fun everyCueIsPlacedEvenWhenThereIsNoRoomToSeparateThem() {
        val boxes = layOutCues(
            List(6) { cue("line $it", line = 50.0) },
            cueHeightPercent = 40.0,
        )

        assertEquals(6, boxes.size)
        assertTrue(boxes.all { it.text.isNotEmpty() })
    }

    @Test
    fun alignmentSurvivesLayout() {
        val boxes = layOutCues(
            listOf(cue(align = CueAlign.End, size = 50.0)),
            cueHeightPercent = 8.0,
        )

        assertEquals(CueAlign.End, boxes.single().align)
        assertEquals(50.0, boxes.single().leftPercent)
    }
}
