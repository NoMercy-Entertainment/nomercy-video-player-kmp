// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import androidx.compose.ui.graphics.ImageBitmap
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.core.cues.parseVttSprite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// What a chrome asks the sprite for while a viewer drags the scrubber.
//
// The tile source is faked because the thing under test is the wiring — which
// index a time maps to, that a not-yet-read frame reads as "draw nothing", and
// that the declared size is available before any pixels are. The real decoders
// are platform I/O and are proven on hardware.
class PreviewSpriteTest {

    private val frames: List<SpriteCue> = parseVttSprite(REAL_SPRITE_VTT)

    @Test
    fun aTimeAsksTheSourceForTheFrameThatTimeLandsOn() {
        // The index is the wiring. Whether pixels come back is the decoder's
        // business, and asking for the wrong frame is the failure that shows a
        // viewer someone else's scene.
        val tiles = RecordingTiles()

        PreviewSprite(frames, tiles).frameAt(20.0)

        assertEquals(listOf(2), tiles.asked)
    }

    @Test
    fun aFrameThatIsStillBeingReadDrawsNothingRatherThanFailing() {
        // The band behind this frame has not landed. A chrome paints nothing
        // this pass and is recomposed when it does — an exception here would
        // take the scrubber down mid-drag instead.
        val tiles = RecordingTiles()
        val sprite = PreviewSprite(frames, tiles)

        assertNull(sprite.frameAt(20.0))
        assertEquals(listOf(2), tiles.asked, "it did not even ask, so the band is never requested")
    }

    @Test
    fun anEmptySheetAsksForNothing() {
        val tiles = RecordingTiles()
        val sprite = PreviewSprite(emptyList(), tiles)

        assertNull(sprite.frameAt(20.0))
        assertEquals(emptyList(), tiles.asked)
    }

    @Test
    fun theDeclaredFrameSizeIsKnownBeforeAnyPixelsAre() {
        // A preview box that sizes itself when the image lands is a box that
        // jumps under the viewer's thumb, so the layout reads these first.
        val sprite = PreviewSprite(frames, RecordingTiles())

        assertEquals(320, sprite.frameWidthPx)
        assertEquals(178, sprite.frameHeightPx)
    }

    @Test
    fun theCueIsAvailableSeparatelyFromThePixels() {
        // The crop and the aspect come from the cue, and both are needed on a
        // pass where the pixels are still missing.
        val sprite = PreviewSprite(frames, RecordingTiles())

        assertEquals(640, sprite.cueAt(25.0)?.x)
    }

    @Test
    fun releasingReachesTheSource() {
        // A player torn down mid-scrub leaves a region decoder open otherwise,
        // and on Android that is a native handle rather than something the GC
        // will get to.
        val tiles = RecordingTiles()

        PreviewSprite(frames, tiles).release()

        assertTrue(tiles.released)
    }
}

// Answers null to everything, which is the honest common-code fake: an
// ImageBitmap cannot be constructed in an Android host test without Robolectric
// standing up a real graphics stack, and a fake that needs one would push this
// whole file onto a single platform. The pixels reaching the caller are proven
// in PreviewSpritePixelsTest, where a real bitmap exists.
private class RecordingTiles : SpriteTileSource {
    val asked: MutableList<Int> = mutableListOf()
    var released: Boolean = false

    override fun frame(index: Int): ImageBitmap? {
        asked.add(index)
        return null
    }

    override fun release() {
        released = true
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
""".trimIndent()
