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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

// The half of PreviewSprite that needs a real bitmap to say anything.
//
// Here rather than in commonTest because constructing an ImageBitmap on the
// Android host-test target calls into android.graphics.Bitmap, which is a stub
// that throws unless Robolectric has stood up a graphics stack. On the JVM
// target Skiko is already present and one costs nothing.
class PreviewSpritePixelsTest {

    @Test
    fun theFramesPixelsReachTheCallerUntouched() {
        // A sprite that re-wrapped or copied what the source handed back would
        // cost a full frame copy per scrub tick, which on a television is the
        // difference between a preview that tracks the thumb and one that lags
        // behind it.
        val pixels: ImageBitmap = ImageBitmap(2, 2)
        val sprite = PreviewSprite(FRAMES, FixedTiles(pixels))

        assertSame(pixels, sprite.frameAt(15.0))
    }

    @Test
    fun releasingReachesTheSourceThatHoldsTheHandle() {
        val tiles = FixedTiles(ImageBitmap(1, 1))

        PreviewSprite(FRAMES, tiles).release()

        assertEquals(1, tiles.releases)
    }
}

private class FixedTiles(private val pixels: ImageBitmap) : SpriteTileSource {
    var releases: Int = 0

    override fun frame(index: Int): ImageBitmap = pixels

    override fun release() {
        releases += 1
    }
}

private val FRAMES = listOf(
    SpriteCue(start = 0.0, end = 10.0, url = "s.webp", x = 0, y = 0, width = 320, height = 178),
    SpriteCue(start = 10.0, end = 20.0, url = "s.webp", x = 320, y = 0, width = 320, height = 178),
)
