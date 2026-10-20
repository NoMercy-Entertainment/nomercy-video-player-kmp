// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

import tv.nomercy.player.video.subtitles.AssImage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

// AndroidFrameCompositor feeds this to an ALPHA_8 bitmap, which reads `width`
// bytes per row. libass pads rows out to the stride, so the rows have to be
// repacked or every row after the first shifts.
class TightCoverageTest {

    private fun run(width: Int, height: Int, stride: Int, pixels: ByteArray) = AssImage(
        x = 0,
        y = 0,
        width = width,
        height = height,
        stride = stride,
        colour = 0,
        pixels = pixels,
    )

    @Test
    fun aPaddedRunComesOutWithEveryRowAtItsOwnColumns() {
        // Width 5, height 3, stride 8: column value is x + 1, padding is 99.
        val padded = ByteArray(8 * 3) { index -> if (index % 8 < 5) (index % 8 + 1).toByte() else 99 }

        val tight: ByteArray? = tightCoverage(run(5, 3, 8, padded))

        assertContentEquals(
            ByteArray(15) { index -> (index % 5 + 1).toByte() },
            tight,
        )
    }

    @Test
    fun aLastRowThatStopsAtTheWidthStillCopies() {
        // libass allocates stride * (h - 1) + w bytes: the last row is not padded.
        val padded = ByteArray(8 * 2 + 5) { index -> if (index % 8 < 5) (index % 8 + 1).toByte() else 99 }

        val tight: ByteArray? = tightCoverage(run(5, 3, 8, padded))

        assertContentEquals(ByteArray(15) { index -> (index % 5 + 1).toByte() }, tight)
    }

    @Test
    fun aTightRunIsCopiedAsIs() {
        val pixels = ByteArray(15) { it.toByte() }

        assertContentEquals(pixels, tightCoverage(run(5, 3, 5, pixels)))
    }

    @Test
    fun aRunShorterThanItsGeometryIsRefused() {
        assertNull(tightCoverage(run(5, 3, 8, ByteArray(10))))
    }
}
