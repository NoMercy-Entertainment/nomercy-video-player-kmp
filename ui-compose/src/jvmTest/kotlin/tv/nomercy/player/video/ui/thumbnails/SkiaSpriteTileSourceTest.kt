// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import tv.nomercy.player.core.cues.SpriteCue

// The desktop decoder, against bytes it actually has to decode.
//
// Every other test here hands the sprite a fake tile source, which is right for
// what those measure and useless for this: the one thing worth knowing about
// this class is whether it cuts the correct rectangle out of a real encoded
// image. So the sheet is built, encoded, and read back — a frame coming out the
// wrong colour means the crop is off by a frame.
class SkiaSpriteTileSourceTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun eachFrameComesBackWithItsOwnRegionOfTheSheet() {
        val source: SpriteTileSource? = spriteTileSource(twoTonedSheet(), FRAMES, scope)
        assertNotNull(source)

        val left: ImageBitmap? = source.frame(0)
        val right: ImageBitmap? = source.frame(1)

        assertNotNull(left)
        assertNotNull(right)
        assertEquals(ComposeColor.Red, left.middlePixel())
        assertEquals(ComposeColor.Blue, right.middlePixel())
    }

    @Test
    fun aFrameComesBackTheSizeItWasDeclared() {
        val source: SpriteTileSource? = spriteTileSource(twoTonedSheet(), FRAMES, scope)

        val frame: ImageBitmap? = source?.frame(0)

        assertEquals(FRAME_EDGE, frame?.width)
        assertEquals(FRAME_EDGE, frame?.height)
    }

    @Test
    fun bytesThatAreNotAnImageOpenNothing() {
        // An HTML error page served with a 200, which is what a reverse proxy in
        // front of a self-hosted server hands back when it is misconfigured.
        assertNull(spriteTileSource("<html>not a sheet</html>".encodeToByteArray(), FRAMES, scope))
    }

    @Test
    fun aFrameOffTheEdgeOfTheSheetIsDeclinedRatherThanRead() {
        // A VTT and a sheet that disagree, which happens when one is regenerated
        // and the other is still cached. Reading past the edge is a crash in the
        // native layer rather than a Kotlin exception.
        val past = listOf(
            SpriteCue(start = 0.0, end = 10.0, url = SHEET_URL, x = 9_000, y = 0, width = 320, height = 178),
        )

        assertNull(spriteTileSource(twoTonedSheet(), past, scope)?.frame(0))
    }

    @Test
    fun aReleasedSourceStopsAnsweringRatherThanReadingAClosedImage() {
        // A player torn down mid-scrub. The chrome's last draw pass can still be
        // in flight, and reading through a closed Skia image is a native crash.
        val source: SpriteTileSource? = spriteTileSource(twoTonedSheet(), FRAMES, scope)
        assertNotNull(source)
        source.frame(0)

        source.release()

        assertNull(source.frame(0))
    }
}

private fun ImageBitmap.middlePixel(): ComposeColor =
    toPixelMap()[width / 2, height / 2]

// Two frames side by side, one red and one blue, encoded as a real PNG.
private fun twoTonedSheet(): ByteArray {
    val surface: Surface = Surface.makeRasterN32Premul(FRAME_EDGE * 2, FRAME_EDGE)

    surface.canvas.drawRect(
        Rect.makeXYWH(0f, 0f, FRAME_EDGE.toFloat(), FRAME_EDGE.toFloat()),
        Paint().apply { color = RED },
    )
    surface.canvas.drawRect(
        Rect.makeXYWH(FRAME_EDGE.toFloat(), 0f, FRAME_EDGE.toFloat(), FRAME_EDGE.toFloat()),
        Paint().apply { color = BLUE },
    )

    return surface.makeImageSnapshot()
        .encodeToData(EncodedImageFormat.PNG)
        ?.bytes
        ?: error("the test sheet did not encode, so nothing below measures anything")
}

private const val SHEET_URL = "s.png"
private const val FRAME_EDGE = 32
private val RED: Int = Color.makeRGB(255, 0, 0)
private val BLUE: Int = Color.makeRGB(0, 0, 255)

private val FRAMES = listOf(
    SpriteCue(
        start = 0.0,
        end = 10.0,
        url = SHEET_URL,
        x = 0,
        y = 0,
        width = FRAME_EDGE,
        height = FRAME_EDGE,
    ),
    SpriteCue(
        start = 10.0,
        end = 20.0,
        url = SHEET_URL,
        x = FRAME_EDGE,
        y = 0,
        width = FRAME_EDGE,
        height = FRAME_EDGE,
    ),
)
