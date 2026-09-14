// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.runBlocking
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssFrameCompositor
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import kotlin.test.Test
import kotlin.test.assertEquals

// With no track the layer drew a new full-screen empty picture on every tick, and
// on a 3 GB phone that ran out of memory opening an episode (2026-09-14).
class AssBlankFrameTest {

    private class SwitchableRenderer : AssRenderer {
        var track: Boolean = false
        override fun addFont(name: String, data: ByteArray): Unit = Unit
        override fun clearFonts(): Unit = Unit
        override fun loadTrack(assContent: String): Unit = Unit
        override fun storageSize(): AssSize? = null
        override fun storageSize(width: Int, height: Int): Unit = Unit
        override fun frameSize(width: Int, height: Int): Unit = Unit
        override fun render(timeMillis: Long): AssFrame? =
            if (track) AssFrame(images = emptyList(), changed = true) else null
        override fun hasTrack(): Boolean = track
        override fun release(): Unit = Unit
    }

    private fun published(renderer: SwitchableRenderer, drawing: AssDrawing, ticks: Int): Int = runBlocking {
        (1..ticks).count { tick -> nextPicture(renderer, drawing, tick * 16L, SIZE) != null }
    }

    @Test
    fun sixtyTicksWithoutATrackPublishOneEmptyPicture() {
        val drawing = AssDrawing(AssFrameCompositor(), AssPictureSurface())

        val count: Int = published(SwitchableRenderer(), drawing, ticks = 60)

        assertEquals(1, count, "an empty picture was drawn on every tick")
    }

    @Test
    fun aTrackTornDownAfterDrawingIsClearedAgain() {
        val renderer = SwitchableRenderer()
        val drawing = AssDrawing(AssFrameCompositor(), AssPictureSurface())
        published(renderer, drawing, ticks = 3)

        renderer.track = true
        published(renderer, drawing, ticks = 1)
        renderer.track = false

        val count: Int = published(renderer, drawing, ticks = 5)

        assertEquals(1, count, "the last cue stayed on screen after its track went away")
    }
}

private val SIZE = IntSize(64, 36)
