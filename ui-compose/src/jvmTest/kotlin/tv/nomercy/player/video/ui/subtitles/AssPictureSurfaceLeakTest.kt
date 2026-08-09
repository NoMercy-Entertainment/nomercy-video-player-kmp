// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import kotlin.test.Test
import kotlin.test.assertTrue
import tv.nomercy.player.video.subtitles.AssFrameCompositor
import tv.nomercy.player.video.subtitles.AssSurfaceFrame

/**
 * How many subtitle pictures are still alive after a long playback.
 *
 * The surface allocates a Skia bitmap per published frame and `installPixels`
 * copies the picture into a native pixel ref. Skia frees that when the Java
 * wrapper is collected; the wrapper is a few dozen bytes, so the heap never
 * grows enough to make a collection worth running and the native side climbs
 * without limit.
 *
 * Pooling one bitmap per compositor slot was the other way out of that, and it
 * was wrong: `asComposeImageBitmap` WRAPS, so rewriting a slot the composition
 * is still painting from crashed the process inside skiko with the subtitle
 * simply absent from the frames before it. So the bitmaps are per frame and the
 * canvas says when it is done with them, which is what this measures.
 *
 * Counted rather than weighed, for the same reason `SkiaFrameSinkLeakTest`
 * counts: a byte threshold measures the collector's mood, and the collector is
 * allowed to free nothing at all and still be correct.
 */
class AssPictureSurfaceLeakTest {

    @Test
    fun aThousandFramesLeaveAtMostTheOneOnScreenAlive() {
        val compositor = AssFrameCompositor()
        val surface = AssPictureSurface()

        // Painting after each publish, because that is what the canvas does and
        // the surface frees nothing until it is told.
        repeat(FRAMES) { index ->
            val frame: AssSurfaceFrame = compositor.render(emptyList(), WIDTH, HEIGHT)
            surface.bitmap(frame, WIDTH, HEIGHT)
            surface.painted(index + 1)
        }

        // One: the picture the canvas last painted, which it may paint again.
        // Anything above that is a bitmap nobody will draw and nobody will free.
        val alive: Int = surface.liveBitmaps()
        assertTrue(alive <= 1, "$alive subtitle pictures still held after $FRAMES frames")
    }

    @Test
    fun aFrameTheCanvasHasNotPaintedIsNotFreed() {
        val compositor = AssFrameCompositor()
        val surface = AssPictureSurface()

        surface.bitmap(compositor.render(emptyList(), WIDTH, HEIGHT), WIDTH, HEIGHT)
        surface.bitmap(compositor.render(emptyList(), WIDTH, HEIGHT), WIDTH, HEIGHT)

        // The canvas has drawn the first and is holding it. Freeing on "up to
        // and including" would release the bitmap currently on screen, which is
        // the read of freed memory this ledger exists to prevent.
        surface.painted(1)

        assertTrue(surface.liveBitmaps() >= 1, "the picture the canvas is showing was freed under it")
    }

    private companion object {
        const val WIDTH: Int = 32
        const val HEIGHT: Int = 32
        const val FRAMES: Int = 1_000
    }
}
