// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Test
import tv.nomercy.player.video.ass.AssImage
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Turning libass coverage into pixels, on a device, because none of this exists
// on a host JVM: Bitmap and Canvas are stubs there that throw.
class FrameCompositorGateTest {

    @Test
    fun aFullyCoveredRunComesOutTheColourLibassAsked() {
        // libass packs 0xRRGGBBAA with an INVERSE alpha byte, so pure opaque red
        // arrives as 0xFF000000. Reading that last byte as ordinary alpha draws
        // every fully-opaque subtitle at zero alpha — nothing on screen, which
        // looks exactly like a subtitle that never arrived.
        val frame: Bitmap = AndroidFrameCompositor().composite(
            listOf(solidRun(colour = OPAQUE_RED)),
            FRAME,
            FRAME,
        )

        assertEquals(Color.RED, frame.getPixel(RUN_EDGE / 2, RUN_EDGE / 2))
    }

    @Test
    fun anInverseAlphaOfFullMeansInvisible() {
        // The other end of the same byte. 0x000000FF is "transparent black", and
        // a compositor reading it as opaque paints a black box over the video.
        val frame: Bitmap = AndroidFrameCompositor().composite(
            listOf(solidRun(colour = TRANSPARENT_BLACK)),
            FRAME,
            FRAME,
        )

        assertEquals(0, Color.alpha(frame.getPixel(RUN_EDGE / 2, RUN_EDGE / 2)))
    }

    @Test
    fun aRunLandsWhereLibassPutIt() {
        // Positions are frame coordinates. Drawing every run at the origin gives
        // a frame that is never blank and never right, and the unit tests cannot
        // tell the difference.
        val frame: Bitmap = AndroidFrameCompositor().composite(
            listOf(solidRun(colour = OPAQUE_RED, x = 100, y = 60)),
            FRAME,
            FRAME,
        )

        assertEquals(Color.RED, frame.getPixel(100 + RUN_EDGE / 2, 60 + RUN_EDGE / 2))
        assertEquals(0, Color.alpha(frame.getPixel(RUN_EDGE / 2, RUN_EDGE / 2)))
    }

    @Test
    fun theFrameIsSoftwareSoItCanBeReadBack() {
        // The most expensive mistake available here. A hardware bitmap cannot be
        // read and Canvas refuses to draw into one, so a frame allocated as one
        // renders black with nothing reported.
        val frame: Bitmap = AndroidFrameCompositor().composite(emptyList(), FRAME, FRAME)

        assertNotEquals(Bitmap.Config.HARDWARE, frame.config)
        assertEquals(Bitmap.Config.ARGB_8888, frame.config)
    }

    @Test
    fun aRunTruncatedInTransitIsSkippedRatherThanRead() {
        // Fewer bytes than the declared rectangle. Handing that to
        // copyPixelsFromBuffer reads past the array in native code.
        val short = AssImage(
            x = 0,
            y = 0,
            width = RUN_EDGE,
            height = RUN_EDGE,
            stride = RUN_EDGE,
            colour = OPAQUE_RED,
            pixels = ByteArray(RUN_EDGE),
        )

        val frame: Bitmap = AndroidFrameCompositor().composite(listOf(short), FRAME, FRAME)

        assertEquals(0, Color.alpha(frame.getPixel(RUN_EDGE / 2, RUN_EDGE / 2)))
    }

    @Test
    fun aFrameHandedBackIsTheSameOneNextTime() {
        // The whole point of the pool. A 1080p frame is eight megabytes, and
        // allocating one per cue change fragments the large-object space until
        // an allocation that should fit does not.
        val pool = BitmapPool()
        val first: Bitmap = pool.obtain(FRAME, FRAME)

        pool.release(first)
        val second: Bitmap = pool.obtain(FRAME, FRAME)

        assertSame(first, second)
    }

    @Test
    fun aReusedFrameComesBackBlank() {
        // Otherwise the previous cue is still on it, and a subtitle that changed
        // shows both lines at once.
        val pool = BitmapPool()
        val first: Bitmap = pool.obtain(RUN_EDGE, RUN_EDGE)
        first.eraseColor(Color.RED)

        pool.release(first)

        assertEquals(0, Color.alpha(pool.obtain(RUN_EDGE, RUN_EDGE).getPixel(0, 0)))
    }

    @Test
    fun aDifferentSizeIsNotReused() {
        // Drawing a smaller frame into a larger one leaves the previous frame's
        // pixels around the edges, which reads as a torn border.
        val pool = BitmapPool()
        val big: Bitmap = pool.obtain(FRAME, FRAME)
        pool.release(big)

        val small: Bitmap = pool.obtain(RUN_EDGE, RUN_EDGE)

        assertEquals(RUN_EDGE, small.width)
        assertNotEquals(big, small)
    }

    @Test
    fun thePoolStopsAtItsBudgetRatherThanGrowing() {
        val pool = BitmapPool(maxBytes = SMALL_POOL)

        repeat(POOLED_ATTEMPTS) { pool.release(Bitmap.createBitmap(FRAME, FRAME, Bitmap.Config.ARGB_8888)) }

        assertTrue(
            pool.pooledByteCount() <= SMALL_POOL,
            "the pool holds ${pool.pooledByteCount()} bytes against a budget of $SMALL_POOL",
        )
    }

    @Test
    fun aFrameHandedBackTwiceIsNotHandedOutTwice() {
        // A caller that releases the same frame on two paths would otherwise get
        // it back for two different cues and draw both onto one bitmap.
        val pool = BitmapPool()
        val frame: Bitmap = pool.obtain(RUN_EDGE, RUN_EDGE)

        pool.release(frame)
        pool.release(frame)

        val first: Bitmap = pool.obtain(RUN_EDGE, RUN_EDGE)
        val second: Bitmap = pool.obtain(RUN_EDGE, RUN_EDGE)

        assertNotEquals(first, second, "the same bitmap was handed out for two frames at once")
    }
}

private fun solidRun(colour: Int, x: Int = 0, y: Int = 0): AssImage = AssImage(
    x = x,
    y = y,
    width = RUN_EDGE,
    height = RUN_EDGE,
    stride = RUN_EDGE,
    colour = colour,
    pixels = ByteArray(RUN_EDGE * RUN_EDGE) { FULL_COVERAGE },
)

private const val FRAME = 320
private const val RUN_EDGE = 32
private const val FULL_COVERAGE = 0xFF.toByte()

// 0xRRGGBBAA with the alpha byte inverted: zero is opaque.
private const val OPAQUE_RED = 0xFF0000_00.toInt()
private const val TRANSPARENT_BLACK = 0x000000FF

private const val SMALL_POOL = 1024 * 1024
private const val POOLED_ATTEMPTS = 8
