// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.thumbnails

import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.core.cues.parseVttSprite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Which frame of the sheet a scrub position lands on.
//
// Driven from real sprite VTT rather than hand-built cues, because the whole
// point of this layer is that Android and iOS stop parsing the sheet themselves
// and agree on one answer — and a hand-built list agrees with whatever built it.
class SpriteFramesTest {

    private val frames: List<SpriteCue> = parseVttSprite(REAL_SPRITE_VTT)

    @Test
    fun theRealSheetParsesIntoFramesWithTheirRegions() {
        assertEquals(4, frames.size)
        assertEquals(0, frames[0].x)
        assertEquals(320, frames[1].x)
        assertEquals(320, frames[0].width)
        assertEquals(178, frames[0].height)
    }

    @Test
    fun aScrubLandsOnTheLastFrameThatHasStarted() {
        assertEquals(0, frameIndexAt(frames, 5.0))
        assertEquals(1, frameIndexAt(frames, 15.0))
        assertEquals(2, frameIndexAt(frames, 20.0), "a position exactly on a start belongs to that frame")
    }

    @Test
    fun aScrubPastTheEndHoldsTheLastFrame() {
        // Sheets are generated at a fixed interval and stop where the encode
        // stopped, so the last few seconds of an item routinely have no frame.
        // Showing nothing there reads as a broken preview; holding the last one
        // is what both the Android and iOS chromes already do.
        assertEquals(frames.lastIndex, frameIndexAt(frames, 9_999.0))
    }

    @Test
    fun aScrubBeforeTheFirstFrameHoldsTheFirst() {
        assertEquals(0, frameIndexAt(frames, -1.0))
    }

    @Test
    fun noFramesMeansNoAnswerRatherThanFrameZero() {
        // The caller draws nothing on null. Returning 0 here would index into an
        // empty list at the call site instead.
        assertNull(frameIndexAt(emptyList(), 5.0))
    }

    @Test
    fun theDeclaredFrameSizeComesFromTheSheetItself() {
        assertEquals(320, spriteFrameWidth(frames))
        assertEquals(178, spriteFrameHeight(frames))
    }

    @Test
    fun anEmptySheetFallsBackToTheSizeTheChromesAssume() {
        // Both existing chromes hard-code 320x178 when a sheet has no cues, and
        // a preview box that collapses to zero is worse than one sized for a
        // frame that never arrives.
        assertEquals(320, spriteFrameWidth(emptyList()))
        assertEquals(178, spriteFrameHeight(emptyList()))
    }
}

// Four cues in the shape the media server writes, including the #xywh fragment
// that carries each frame's region within the sheet.
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
