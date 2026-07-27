// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

import java.awt.image.BufferedImage
import tv.nomercy.player.video.ass.AssImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Laying libass coverage into a desktop frame.
//
// No natives are needed for any of this, which is the point of testing it here:
// the compositor is arithmetic over bytes, and it can be held to exact pixels
// rather than to "something was drawn".
class JvmFrameCompositorTest {

    private val compositor = JvmFrameCompositor()

    @Test
    fun aFullyCoveredRunComesOutTheColourLibassAsked() {
        // 0xRRGGBBAA with an INVERSE alpha byte, so opaque red is 0xFF000000.
        // Reading that byte as ordinary alpha draws every fully-opaque subtitle
        // at zero alpha — nothing on screen, which is what a subtitle that never
        // arrived also looks like.
        val frame: BufferedImage = compositor.composite(listOf(solidRun(OPAQUE_RED)), FRAME, FRAME)

        assertEquals(OPAQUE_ARGB_RED, frame.getRGB(RUN / 2, RUN / 2))
    }

    @Test
    fun anInverseAlphaOfFullDrawsNothing() {
        // 0x000000FF is "transparent black". A compositor reading it as opaque
        // paints a black box over the video.
        val frame: BufferedImage = compositor.composite(listOf(solidRun(TRANSPARENT_BLACK)), FRAME, FRAME)

        assertEquals(0, frame.getRGB(RUN / 2, RUN / 2))
    }

    @Test
    fun aRunLandsWhereLibassPutIt() {
        val frame: BufferedImage = compositor.composite(
            listOf(solidRun(OPAQUE_RED, x = 90, y = 40)),
            FRAME,
            FRAME,
        )

        assertEquals(OPAQUE_ARGB_RED, frame.getRGB(90 + RUN / 2, 40 + RUN / 2))
        assertEquals(0, frame.getRGB(RUN / 2, RUN / 2))
    }

    @Test
    fun halfCoverageComesOutHalfOpaque() {
        // The anti-aliased edge of every glyph. Treating coverage as on-or-off
        // is what makes subtitle text look like it was drawn in the nineties.
        val half = solidRun(OPAQUE_RED, coverage = 0x80.toByte())

        val frame: BufferedImage = compositor.composite(listOf(half), FRAME, FRAME)

        val alpha: Int = (frame.getRGB(RUN / 2, RUN / 2) ushr 24) and 0xFF
        assertTrue(alpha in 120..136, "half coverage came out at alpha $alpha")
    }

    @Test
    fun aGlyphDrawnOverItsOutlineKeepsBoth() {
        // Runs arrive back to front: the outline first, then the glyph over it.
        // A compositor that replaced rather than blended would lose the outline
        // entirely, and one that drew in the other order would hide the glyph.
        val outline = solidRun(OPAQUE_BLUE)
        val glyph = solidRun(OPAQUE_RED, coverage = 0x80.toByte())

        val frame: BufferedImage = compositor.composite(listOf(outline, glyph), FRAME, FRAME)
        val pixel: Int = frame.getRGB(RUN / 2, RUN / 2)

        val red: Int = (pixel ushr 16) and 0xFF
        val blue: Int = pixel and 0xFF
        assertTrue(red > 0, "the glyph did not reach the frame")
        assertTrue(blue > 0, "the outline under it was replaced rather than blended")
    }

    @Test
    fun aRunOffTheEdgeIsClippedRatherThanThrowing() {
        // Positions come from the subtitle, which was authored against another
        // resolution. Writing past the frame is an exception per rendered frame.
        val frame: BufferedImage = compositor.composite(
            listOf(solidRun(OPAQUE_RED, x = FRAME - 4, y = FRAME - 4)),
            FRAME,
            FRAME,
        )

        assertEquals(OPAQUE_ARGB_RED, frame.getRGB(FRAME - 1, FRAME - 1))
    }

    @Test
    fun aRunTruncatedInTransitIsSkipped() {
        // Fewer bytes than the declared rectangle. Indexing on regardless is an
        // out-of-bounds on every frame the track is on screen.
        val short = AssImage(
            x = 0,
            y = 0,
            width = RUN,
            height = RUN,
            stride = RUN,
            colour = OPAQUE_RED,
            pixels = ByteArray(RUN),
        )

        val frame: BufferedImage = compositor.composite(listOf(short), FRAME, FRAME)

        assertEquals(0, frame.getRGB(RUN / 2, RUN / 2))
    }

    @Test
    fun strideIsRespectedRatherThanAssumedEqualToWidth() {
        // libass pads rows. Reading width bytes per row instead of stride walks
        // the run diagonally, which draws a glyph that slants.
        val padded = AssImage(
            x = 0,
            y = 0,
            width = 2,
            height = 2,
            stride = 4,
            colour = OPAQUE_RED,
            pixels = byteArrayOf(
                0xFF.toByte(), 0xFF.toByte(), 0, 0,
                0xFF.toByte(), 0xFF.toByte(), 0, 0,
            ),
        )

        val frame: BufferedImage = compositor.composite(listOf(padded), FRAME, FRAME)

        assertEquals(OPAQUE_ARGB_RED, frame.getRGB(1, 1))
        assertEquals(0, frame.getRGB(2, 0), "padding bytes were drawn as pixels")
    }
}

private fun solidRun(
    colour: Int,
    x: Int = 0,
    y: Int = 0,
    coverage: Byte = 0xFF.toByte(),
): AssImage = AssImage(
    x = x,
    y = y,
    width = RUN,
    height = RUN,
    stride = RUN,
    colour = colour,
    pixels = ByteArray(RUN * RUN) { coverage },
)

private const val FRAME = 240
private const val RUN = 32

private const val OPAQUE_RED = 0xFF0000_00.toInt()
private const val OPAQUE_BLUE = 0x0000FF_00
private const val TRANSPARENT_BLACK = 0x000000FF
private const val OPAQUE_ARGB_RED = 0xFFFF0000.toInt()
