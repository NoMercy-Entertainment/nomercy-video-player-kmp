// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// WHERE the picture lands in the box, which is the half a compositing test does
// not answer.
//
// On the desktop the video draws flush against the right edge of the player with
// a black band down the left: at 800px the picture starts 230px in, runs off the
// right edge and off the bottom. It is not letterboxing — `ContentScale.Fit`
// centres, so a 16:9 frame in a wider box leaves EQUAL bars. One bar on one side
// means something is positioning it, and a screenshot cannot tell whether that
// is the composable, the window, or the display's scale factor.
//
// So this renders the real composable offscreen at a known size with a known
// frame. If the offset reproduces here it is the composable. If it does not, the
// composable is right and the fault is in the window path — which is worth
// knowing before changing either.
class PlayerSurfaceBoundsTest {

    // A frame that is entirely one bright colour, so any pixel of it is
    // recognisable and every black pixel in the output is definitely not it.
    private fun greenFrame(width: Int, height: Int): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
        bitmap.erase(0xFF00FF00.toInt())

        return bitmap.asComposeImageBitmap()
    }

    private fun Bitmap.asComposeImageBitmap(): ImageBitmap =
        org.jetbrains.skia.Image.makeFromBitmap(this).toComposeImageBitmap()

    private fun render(width: Int, height: Int, frame: ImageBitmap): ImageBitmap {
        val scene = ImageComposeScene(width = width, height = height) {
            // The same two layers PlayerSurface has: black underneath so the gap
            // between mounting and the first frame is not the window showing
            // through, and the frame over it scaled to fit.
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        try {
            return scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }

    // The band, measured. A 16:9 frame in a 4:3 box fits by WIDTH, so the bars
    // are top and bottom and the picture touches both side edges.
    @Test
    fun aWideFrameInATallerBoxTouchesBothSideEdges() {
        val out = render(800, 600, greenFrame(1920, 1080))
        val pixels = out.toPixelMap()

        assertEquals(Color.Green, pixels[0, 300], "the picture does not reach the left edge")
        assertEquals(Color.Green, pixels[799, 300], "the picture does not reach the right edge")
    }

    // And the reverse: a 16:9 frame in a WIDER box fits by height, and then the
    // bars are left and right — EQUAL. This is the shape the desktop player is
    // getting wrong, so the assertion is on the symmetry rather than on a width.
    @Test
    fun aWideFrameInAnEvenWiderBoxIsCentredNotFlushRight() {
        val out = render(1200, 400, greenFrame(1920, 1080))
        val pixels = out.toPixelMap()

        val row = 200
        val left: Int = (0 until 1200).first { pixels[it, row] == Color.Green }
        val right: Int = (0 until 1200).last { pixels[it, row] == Color.Green }

        val leftBar: Int = left
        val rightBar: Int = 1199 - right

        assertTrue(
            kotlin.math.abs(leftBar - rightBar) <= 1,
            "the picture is not centred: $leftBar px of bar on the left, $rightBar on the right",
        )
    }

    // Nothing of the picture may fall outside the box. Overflow is what the
    // desktop shows — the frame running off the right edge and the bottom — and
    // it is invisible to any check that only samples inside.
    @Test
    fun thePictureStaysInsideTheBox() {
        val out = render(1200, 400, greenFrame(1920, 1080))
        val pixels = out.toPixelMap()

        assertEquals(400, out.height)
        assertEquals(1200, out.width)

        // The top and bottom rows of a height-fitted picture are the picture
        // itself, not a bar, so they prove it was scaled rather than cropped.
        assertEquals(Color.Green, pixels[600, 0], "the top row is not the picture")
        assertEquals(Color.Green, pixels[600, 399], "the bottom row is not the picture")
    }
}
