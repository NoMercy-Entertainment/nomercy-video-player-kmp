// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.ui.graphics.toPixelMap
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import tv.nomercy.player.video.subtitles.AssFrameCompositor
import tv.nomercy.player.video.subtitles.AssImage
import kotlin.test.assertEquals

// The colour a cue is drawn in, on the way out of the compositor.
//
// An ARGB_8888 bitmap holds its bytes R,G,B,A and copyPixelsFromBuffer is a
// memcpy, so a little-endian ARGB int lands red-where-blue-was. No Game No
// Life's dialogue outline is authored &H00833F39 — a dark navy — and it drew
// warm orange on a real phone while the same line rendered off-screen drew
// navy. Nothing failed; the outline simply stopped holding the text off the
// picture.
//
// On the device, deliberately. Robolectric's shadow Bitmap round-trips an ARGB
// int unchanged, so it agrees with either byte order and can arbitrate neither.
@RunWith(AndroidJUnit4::class)
class AssPictureChannelsDeviceTest {

    @Test
    fun aCueKeepsTheColourItWasAuthoredIn() {
        // libass packs 0xRRGGBBAA with an INVERSE alpha byte, so this is the
        // navy &H00833F39 resolves to, fully opaque.
        val navy = 0x393F83.shl(BYTE) or 0x00
        val drawn = drawOnePixel(navy)

        assertEquals(0x39, drawn.red, "red")
        assertEquals(0x3F, drawn.green, "green")
        assertEquals(0x83, drawn.blue, "blue")
    }

    // Red and blue apart, so a swap cannot pass by symmetry the way a grey or a
    // white one would.
    @Test
    fun redIsNotDrawnAsBlue() {
        val drawn = drawOnePixel(0xFF0000.shl(BYTE) or 0x00)

        assertEquals(0xFF, drawn.red, "red")
        assertEquals(0x00, drawn.blue, "blue")
    }

    private fun drawOnePixel(libassColour: Int): Channels {
        val image = AssImage(
            width = 1,
            height = 1,
            stride = 1,
            x = 0,
            y = 0,
            colour = libassColour,
            // Full coverage: one solid pixel of the run's own colour.
            pixels = byteArrayOf(0xFF.toByte()),
        )
        val frame = AssFrameCompositor().render(listOf(image), 1, 1)
        val pixel = AssPictureSurface().bitmap(frame, 1, 1).toPixelMap()[0, 0]

        return Channels(
            red = (pixel.red * MAX_CHANNEL).toInt(),
            green = (pixel.green * MAX_CHANNEL).toInt(),
            blue = (pixel.blue * MAX_CHANNEL).toInt(),
        )
    }

    private data class Channels(val red: Int, val green: Int, val blue: Int)
}

private const val BYTE = 8
private const val MAX_CHANNEL = 255f
