// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.core.cues.parseVttSprite
import kotlin.test.Test
import kotlin.test.assertEquals

// What a remote does to the seek strip.
//
// The sheet here is FOUR seconds a frame, deliberately. A step in seconds and a
// step in frames give the same answer on a ten-second sheet, so a sheet at that
// interval cannot tell the two interactions apart — and telling them apart is
// the whole subject. Every position below is a number only frame-stepping
// reaches.
class TvSeekStripTest {

    private val frames: List<SpriteCue> = parseVttSprite(FOUR_SECOND_SHEET)

    private val strip = SeekStrip(frames, WHOLE_FILM)

    private fun press(key: Key, from: SeekCursor): SeekPress =
        pressFor(key, KeyEventType.KeyDown, from, strip)

    @Test
    fun aSidewaysPressMovesOneFrameRatherThanAFixedNumberOfSeconds() {
        // One press, one thumbnail. A fixed step in seconds walks off the strip
        // the viewer is looking at: at ten seconds a press on this sheet the
        // first press would already have skipped two frames, so the picture
        // under the ring would not be the picture they were shown.
        val moved: SeekPress = press(Key.DirectionRight, strip.cursorAt(0.0))

        assertEquals(1, moved.cursor.index)
        assertEquals(FRAME_SECONDS, moved.cursor.seconds)
        assertEquals(SeekOutcome.MOVED, moved.outcome)
    }

    @Test
    fun theSecondComesOffTheFrameItLandedOnRatherThanFromTheStep() {
        // Read from the cue, not computed. A sheet's interval is whatever
        // generated it, and multiplying a step by an assumed one drifts away
        // from the frames as the presses accumulate.
        var cursor: SeekCursor = strip.cursorAt(0.0)

        repeat(THREE_PRESSES) { cursor = press(Key.DirectionRight, cursor).cursor }

        assertEquals(THREE_PRESSES, cursor.index)
        assertEquals(frames[THREE_PRESSES].start, cursor.seconds)
    }

    @Test
    fun itMovesBackwardsByAFrameToo() {
        val start: SeekCursor = strip.cursorAt(frames.last().start)

        val moved: SeekPress = press(Key.DirectionLeft, start)

        assertEquals(frames.lastIndex - 1, moved.cursor.index)
        assertEquals(frames[frames.lastIndex - 1].start, moved.cursor.seconds)
    }

    @Test
    fun itWillNotStepOffEitherEndOfTheSheet() {
        // Off the end is a frame that does not exist, and an index computed past
        // one is a crash on the very press a viewer makes when they are looking
        // for the beginning.
        val atStart: SeekPress = press(Key.DirectionLeft, strip.cursorAt(0.0))
        val atEnd: SeekPress = press(Key.DirectionRight, strip.cursorAt(frames.last().start))

        assertEquals(0, atStart.cursor.index)
        assertEquals(frames.lastIndex, atEnd.cursor.index)
    }

    @Test
    fun committingTakesTheFramesOwnTimeAndNotAStep() {
        var cursor: SeekCursor = strip.cursorAt(0.0)
        cursor = press(Key.DirectionRight, cursor).cursor
        cursor = press(Key.DirectionRight, cursor).cursor

        val committed: SeekPress = press(Key.DirectionCenter, cursor)

        assertEquals(SeekOutcome.COMMITTED, committed.outcome)
        assertEquals(frames[2].start, committed.cursor.seconds)
    }

    @Test
    fun whereTheFilmIsBecomesAPlaceInTheStrip() {
        // Entering the strip has to start under the ring at the frame that is on
        // screen. Starting at zero would make every scrub begin by walking back
        // to where the viewer already was.
        assertEquals(2, strip.cursorAt(TWO_FRAMES_IN).index)
    }

    @Test
    fun aKeyGoingUpMovesNothing() {
        // Both halves of a press arrive. Acting on each of them steps twice for
        // every button a viewer touches.
        val cursor: SeekCursor = strip.cursorAt(0.0)

        val released: SeekPress = pressFor(Key.DirectionRight, KeyEventType.KeyUp, cursor, strip)

        assertEquals(SeekOutcome.IGNORED, released.outcome)
        assertEquals(cursor, released.cursor)
    }

    @Test
    fun withNoSheetTheArrowsFallBackToStepsInSeconds() {
        // His container draws nothing and handles no keys without a sheet, which
        // leaves a host that supplied no thumbnails unable to scrub at all. This
        // is the one place the port is deliberately more than the original.
        val sheetless = SeekStrip(emptyList(), WHOLE_FILM)
        val cursor: SeekCursor = sheetless.cursorAt(START)

        val moved: SeekCursor = pressFor(Key.DirectionRight, KeyEventType.KeyDown, cursor, sheetless).cursor

        assertEquals(START + TEN_SECONDS, moved.seconds)
    }

    @Test
    fun andTheSecondsFallbackStaysInsideTheFilm() {
        val sheetless = SeekStrip(emptyList(), WHOLE_FILM)

        val past: SeekCursor =
            pressFor(Key.DirectionRight, KeyEventType.KeyDown, sheetless.cursorAt(WHOLE_FILM), sheetless).cursor
        val before: SeekCursor =
            pressFor(Key.DirectionLeft, KeyEventType.KeyDown, sheetless.cursorAt(0.0), sheetless).cursor

        assertEquals(WHOLE_FILM, past.seconds)
        assertEquals(0.0, before.seconds)
    }
}

// Four seconds a frame, which is not the ten a seconds-based step would take.
// Generated the way a real one is — parsed out of the VTT the media server
// serves — rather than hand-built cues, so a change to the parser is visible
// here rather than being mocked away.
private val FOUR_SECOND_SHEET = """
WEBVTT

00:00:00.000 --> 00:00:04.000
sprite.webp#xywh=0,0,320,178

00:00:04.000 --> 00:00:08.000
sprite.webp#xywh=320,0,320,178

00:00:08.000 --> 00:00:12.000
sprite.webp#xywh=640,0,320,178

00:00:12.000 --> 00:00:16.000
sprite.webp#xywh=960,0,320,178

00:00:16.000 --> 00:00:20.000
sprite.webp#xywh=1280,0,320,178
""".trimIndent()

private const val FRAME_SECONDS = 4.0
private const val TWO_FRAMES_IN = 9.0
private const val THREE_PRESSES = 3
private const val WHOLE_FILM = 600.0
private const val START = 60.0
private const val TEN_SECONDS = 10.0
