// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.chapters.ChapterController
import tv.nomercy.player.core.cues.ChapterCues
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.core.media.Chapter
import tv.nomercy.player.video.chapters.ChapterMarker
import tv.nomercy.player.video.chapters.chapterFill
import tv.nomercy.player.video.chapters.chapterMarkers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// The whole path, from the bytes a server sends to what a chrome draws at one
// moment of a scrub.
//
// Every stage of this has its own test, and each of those passes with the stage
// either side of it wired to something else. This is the one that fails if two
// stages agree about a name and disagree about a meaning — which is the failure
// this slice exists to remove, three clients having each parsed these two files
// their own way.
class PreviewPipelineTest {

    private val duration: Double = 1_435.0

    @Test
    fun aScrubToTwentyFiveSecondsResolvesToTheThirdFrameOfTheSheet() = runTest {
        val sprite: PreviewSprite? = loadPreviewSprite(
            spriteUrl = "https://server.test/sprite.webp",
            vttUrl = "https://server.test/sprite.vtt",
            fetch = SpriteFetchers(
                bytes = { ByteArray(8) },
                text = { REAL_SPRITE_VTT },
            ),
            scope = this,
            openTiles = fakeTiles,
        )

        assertNotNull(sprite)

        // Twenty-five seconds in, on a sheet whose frames are ten seconds apart:
        // the third cue, which sits 640 pixels across the sheet.
        val cue: SpriteCue? = sprite.cueAt(25.0)

        assertEquals(640, cue?.x)
        assertEquals(0, cue?.y)
        assertEquals(320, cue?.width)
    }

    @Test
    fun theSameScrubLandsInsideTheThirdChapterAndFillsItPartWay() {
        // The chapter half of the same moment, from the same file a library
        // holds. Twenty-five seconds is inside "Opening", which runs from
        // thirty-three seconds — so it is BEFORE that chapter, in "Prologue",
        // and the marker for Prologue is the one filling.
        val controller = ChapterController { "Chapter" }
        controller.durationChanged(duration)
        controller.ingest(ChapterCues.parse(REAL_CHAPTERS_VTT))

        val chapters: List<Chapter> = controller.chapters()
        val markers: List<ChapterMarker> = chapterMarkers(chapters, duration)

        assertEquals(7, chapters.size)
        assertEquals(7, markers.size)

        val atScrub: Double = 25.0 / duration * 100.0
        val prologue: ChapterMarker = markers.first()

        assertTrue(chapterFill(prologue, atScrub) > 0.0, "the first chapter had not started filling")
        assertTrue(chapterFill(prologue, atScrub) < 1.0, "the first chapter was already full")
        assertEquals(0.0, chapterFill(markers[1], atScrub), "a later chapter was filling too")
    }

    @Test
    fun aFilmWhoseChaptersStartLateIsStillCoveredFromZero() {
        // The case the gap fill exists for, driven the whole way through: a scan
        // that labelled nothing before the first chapter, so a viewer scrubbing
        // into the opening credits would be inside no chapter and the bar would
        // have a hole at its left end.
        val controller = ChapterController { "Chapter" }
        controller.durationChanged(duration)
        controller.ingest(listOf(Chapter(startTime = 60.0, title = "Part A")))

        val markers: List<ChapterMarker> = chapterMarkers(controller.chapters(), duration)

        assertEquals(2, markers.size)
        assertEquals(0.0, markers.first().leftPercent)
        assertEquals(100.0, markers.last().rightPercent)
        assertTrue(controller.chapters().first().synthetic)
    }

    @Test
    fun theBarIsContinuousFromTheFirstFrameToTheLast() {
        // Both halves at once: every marker meets the next, and the strip covers
        // the item end to end. A sliver between two of them draws as a dark line
        // across a light theme, and a gap at either end reads as a broken bar.
        val controller = ChapterController { "Chapter" }
        controller.durationChanged(duration)
        controller.ingest(ChapterCues.parse(REAL_CHAPTERS_VTT))

        val markers: List<ChapterMarker> = chapterMarkers(controller.chapters(), duration)

        assertEquals(0.0, markers.first().leftPercent)
        assertEquals(100.0, markers.last().rightPercent)
        markers.zipWithNext { left, right -> assertEquals(left.rightPercent, right.leftPercent) }
    }
}

private val fakeTiles: (ByteArray, List<SpriteCue>, CoroutineScope) -> SpriteTileSource =
    { _, _, _ ->
        object : SpriteTileSource {
            override fun frame(index: Int): ImageBitmap? = null
            override fun release() = Unit
        }
    }

private val REAL_SPRITE_VTT = """
WEBVTT

00:00:00.000 --> 00:00:10.000
sprite.webp#xywh=0,0,320,178

00:00:10.000 --> 00:00:20.000
sprite.webp#xywh=320,0,320,178

00:00:20.000 --> 00:00:30.000
sprite.webp#xywh=640,0,320,178

00:00:30.000 --> 00:00:40.000
sprite.webp#xywh=960,0,320,178
""".trimIndent()

private val REAL_CHAPTERS_VTT = """
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
