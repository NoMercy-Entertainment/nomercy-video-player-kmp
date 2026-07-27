// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.chapters

import tv.nomercy.player.core.cues.ChapterCues
import tv.nomercy.player.core.media.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Where the segments of a chaptered scrubber sit, and how far each one is filled.
//
// Driven from a real chapters.vtt so the marker positions are checked against
// what a library file actually contains rather than against numbers picked to
// make the arithmetic tidy.
class ChapterMarkerMathTest {

    private val chapters: List<Chapter> = ChapterCues.parse(REAL_VTT)
    private val duration: Double = 1_435.0

    @Test
    fun eachChapterRunsFromItsOwnStartToTheNextOnes() {
        // A Chapter carries a start and no end, so a marker's right edge is the
        // next chapter's left edge. Reading an end off the chapter itself is what
        // the web player does, and it is the one thing that cannot be ported.
        val markers: List<ChapterMarker> = chapterMarkers(chapters, duration)

        assertEquals(chapters.size, markers.size)
        assertEquals(0.0, markers.first().leftPercent)
        assertEquals(33.0 / duration * 100.0, markers.first().rightPercent)
        assertEquals(markers[0].rightPercent, markers[1].leftPercent, "a gap opened between two markers")
    }

    @Test
    fun theLastChapterRunsToTheEndOfTheItem() {
        // Otherwise the bar stops short of its own right edge and the last few
        // seconds of every title are un-segmented.
        val markers: List<ChapterMarker> = chapterMarkers(chapters, duration)

        assertEquals(100.0, markers.last().rightPercent)
    }

    @Test
    fun aDurationNobodyKnowsDrawsNoSegments() {
        // Before the container is read there is nothing to divide by, and every
        // marker would land at zero width on top of every other.
        assertTrue(chapterMarkers(chapters, 0.0).isEmpty())
        assertTrue(chapterMarkers(chapters, -1.0).isEmpty())
        assertTrue(chapterMarkers(emptyList(), duration).isEmpty())
    }

    @Test
    fun aSegmentBehindThePlayheadIsFullAndOneAheadIsEmpty() {
        val markers: List<ChapterMarker> = chapterMarkers(chapters, duration)
        val second: ChapterMarker = markers[1]

        assertEquals(1.0, chapterFill(second, second.rightPercent + 5.0))
        assertEquals(0.0, chapterFill(second, second.leftPercent - 5.0))
    }

    @Test
    fun theSegmentUnderThePlayheadFillsInProportion() {
        val marker = ChapterMarker(index = 0, leftPercent = 10.0, rightPercent = 30.0)

        assertEquals(0.5, chapterFill(marker, 20.0))
        assertEquals(0.25, chapterFill(marker, 15.0))
    }

    @Test
    fun aSegmentWithNoWidthAnswersRatherThanDividingByZero() {
        // Two chapters starting at the same second, which a scan produces on a
        // one-frame segment. Every point around it has to answer without the
        // division being reached, or the layout gets a NaN scale.
        val flat = ChapterMarker(index = 0, leftPercent = 40.0, rightPercent = 40.0)

        assertEquals(0.0, chapterFill(flat, 39.0))
        assertEquals(0.0, chapterFill(flat, 40.0))
        assertEquals(1.0, chapterFill(flat, 41.0))
    }

    @Test
    fun aVeryNarrowSegmentFillsInProportionLikeAnyOther() {
        // The web player guards its division with a minimum span, and that guard
        // is unreachable there for the same reason it is here. Porting it made a
        // segment this narrow read a tenth full where it is a quarter full —
        // which is what a chapter of well under a second on a long film is.
        val narrow = ChapterMarker(index = 0, leftPercent = 40.0, rightPercent = 40.00004)

        val fill: Double = chapterFill(narrow, 40.00001)

        assertTrue(fill > 0.2 && fill < 0.3, "a narrow segment filled to $fill, not a quarter")
    }

    @Test
    fun everySegmentEndsWhereTheNextBegins() {
        // The bar is one continuous strip. A rounding difference that leaves a
        // sliver between two markers shows as a dark line on a light theme.
        val markers: List<ChapterMarker> = chapterMarkers(chapters, duration)

        markers.zipWithNext { left, right ->
            assertEquals(left.rightPercent, right.leftPercent)
        }
    }
}

private val REAL_VTT = """
WEBVTT

Chapter 1
00:00:00 --> 00:00:33
Prologue
Chapter 2
00:00:33 --> 00:02:03
Opening
Chapter 3
00:02:03 --> 00:12:11
Part A
Chapter 4
00:12:11 --> 00:12:18
Eyecatch
Chapter 5
00:12:18 --> 00:21:51
Part B
Chapter 6
00:21:51 --> 00:23:21
Ending
Chapter 7
00:23:21 --> 00:23:55
Epilogue
""".trimIndent()
