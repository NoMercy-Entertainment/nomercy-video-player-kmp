// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.graphics.ImageBitmap
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.video.ui.thumbnails.PreviewSprite
import tv.nomercy.player.video.ui.thumbnails.SpriteTileSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// The preview's pixels reach the chrome.
//
// Every piece of this existed and none of it was joined up. loadPreviewSprite
// built a PreviewSprite and had zero callers; ChapterScrubber took an onPreview
// and the assembly never passed one; ScrubPreview took a `frame` and was called
// without it. Three parameters short of working, and the whole apparatus read as
// implemented — a viewer dragging the bar saw a clock and a chapter name over an
// empty box while the same drag in a browser showed the scene.
//
// So this drives the lookup the chrome performs, by recording which tile the
// sprite asks its source for. Asserting `previewSprite != null` would have passed
// on all three of those bugs.
class ScrubPreviewWiringTest {

    @Test
    fun theSpriteAsksForTheTileCoveringTheScrubTime() {
        val tiles = RecordingTiles()
        val sprite = PreviewSprite(listOf(cue(0.0, 10.0), cue(10.0, 20.0)), tiles)

        sprite.frameAt(4.0)
        sprite.frameAt(14.0)

        assertEquals(listOf(0, 1), tiles.asked)
    }

    @Test
    fun theBoxIsSizedFromWhatTheSheetDeclares() {
        // Before any pixels land, which is why the chrome reads these rather than
        // the bitmap: a box that grows when the image arrives is a box that jumps
        // under the thumb dragging it.
        val sprite = PreviewSprite(listOf(cue(0.0, 10.0)), RecordingTiles())

        assertEquals(FRAME_WIDTH, sprite.frameWidthPx)
        assertEquals(FRAME_HEIGHT, sprite.frameHeightPx)
    }

    @Test
    fun aTimeInsideTheLastCueResolvesToACue() {
        val sprite = PreviewSprite(listOf(cue(0.0, 10.0)), RecordingTiles())

        assertNotNull(sprite.cueAt(9.999), "the last cue does not cover its own range")
    }

    @Test
    fun aSpriteWithNoCuesAsksForNothingRatherThanFrameZero() {
        // A sheet for an item too short to have frames, or a VTT that arrived
        // truncated. Falling back to index zero would draw the opening shot under
        // every position on the bar.
        val tiles = RecordingTiles()

        PreviewSprite(emptyList(), tiles).frameAt(1.0)

        assertEquals(emptyList(), tiles.asked)
        assertNull(PreviewSprite(emptyList(), RecordingTiles()).cueAt(1.0))
    }
}

private const val FRAME_WIDTH = 320
private const val FRAME_HEIGHT = 178

private fun cue(start: Double, end: Double): SpriteCue = SpriteCue(
    start = start,
    end = end,
    url = "thumbs.webp",
    x = 0,
    y = 0,
    width = FRAME_WIDTH,
    height = FRAME_HEIGHT,
)

// Records which tile was wanted and hands back nothing.
//
// No real ImageBitmap: constructing one needs a graphics context a plain unit
// test does not have, and the question here is which index the lookup chose, not
// whether pixels can be allocated.
private class RecordingTiles : SpriteTileSource {
    val asked: MutableList<Int> = mutableListOf()

    override fun frame(index: Int): ImageBitmap? {
        asked += index
        return null
    }

    override fun release() = Unit
}
