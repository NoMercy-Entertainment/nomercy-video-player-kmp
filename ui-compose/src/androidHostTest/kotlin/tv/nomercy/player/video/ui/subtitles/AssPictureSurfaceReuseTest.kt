// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.nomercy.player.video.subtitles.AssFrameCompositor
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

// Each subtitle frame took a new full-screen bitmap, and a busy sign track ran a
// phone out of native memory opening an episode (2026-09-14).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK_UNDER_TEST])
class AssPictureSurfaceReuseTest {

    private fun drawnBitmaps(frames: Int, width: Int, height: Int): List<Bitmap> = runBlocking {
        val compositor = AssFrameCompositor()
        val surface = AssPictureSurface()
        (1..frames).map {
            val frame = compositor.renderParallel(emptyList(), width, height)
            surface.bitmap(frame, width, height).asAndroidBitmap()
        }
    }

    @Test
    fun twelveFramesDrawIntoOneBitmapPerSlot() {
        val drawn: List<Bitmap> = drawnBitmaps(frames = 12, width = WIDTH, height = HEIGHT)

        assertEquals(2, drawn.map { System.identityHashCode(it) }.distinct().size, "a frame allocated its own bitmap")
    }

    @Test
    fun consecutiveFramesUseDifferentSlotsSoTheShownOneIsNotOverwritten() {
        val drawn: List<Bitmap> = drawnBitmaps(frames = 2, width = WIDTH, height = HEIGHT)

        assertNotSame(drawn[0], drawn[1], "the frame on screen was written over by the next")
    }

    @Test
    fun aResizedSurfaceGetsBitmapsOfTheNewSize() = runBlocking {
        val compositor = AssFrameCompositor()
        val surface = AssPictureSurface()
        surface.bitmap(compositor.renderParallel(emptyList(), WIDTH, HEIGHT), WIDTH, HEIGHT)
        val resized: Bitmap = surface.bitmap(compositor.renderParallel(emptyList(), 64, 36), 64, 36).asAndroidBitmap()

        assertEquals(64 to 36, resized.width to resized.height, "a resize drew into the old size")
    }
}

private const val SDK_UNDER_TEST = 34
private const val WIDTH = 32
private const val HEIGHT = 18
