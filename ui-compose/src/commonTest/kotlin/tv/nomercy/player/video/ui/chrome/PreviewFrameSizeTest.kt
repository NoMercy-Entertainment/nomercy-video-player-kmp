// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SHARE = 0.25f
private val FLOOR = 128.dp
private const val TILE_ASPECT = 0.5625f

// How big the scrub bubble draws its frame.
//
// The crash this exists for: seeking threw
// "Cannot coerce value to an empty range: maximum 64.0.dp is less than minimum
// 128.0.dp" the moment the sheet's tile was smaller than the readability floor,
// which every 160x68 sheet is on a phone. A clamp with two variable bounds is
// only a clamp while they are the right way round.
class PreviewFrameSizeTest {

    @Test
    fun aTileSmallerThanTheFloorIsDrawnAtItsOwnSizeRatherThanThrowing() {
        val size: DpSize = previewFrameSize(
            barWidth = 360.dp,
            share = SHARE,
            minWidth = FLOOR,
            tile = DpSize(56.dp, 31.5.dp),
        )

        assertEquals(56.dp, size.width, "a 56dp sheet was stretched to the floor")
    }

    @Test
    fun theFloorStillHoldsOnANarrowBarWithASheetBigEnoughForIt() {
        // 200 * 0.25 = 50dp, under the floor, and the sheet has 320dp to give.
        val size: DpSize = previewFrameSize(
            barWidth = 200.dp,
            share = SHARE,
            minWidth = FLOOR,
            tile = DpSize(320.dp, 180.dp),
        )

        assertEquals(FLOOR, size.width, "the readability floor stopped applying")
    }

    @Test
    fun aWideBarIsCappedAtThePixelsTheSheetActuallyHas() {
        val size: DpSize = previewFrameSize(
            barWidth = 4000.dp,
            share = SHARE,
            minWidth = FLOOR,
            tile = DpSize(320.dp, 180.dp),
        )

        assertEquals(320.dp, size.width, "the bubble was upscaled past the sheet")
    }

    @Test
    fun theSheetsAspectSurvivesEveryClamp() {
        // Every branch, because the height is derived after the width and a clamp
        // applied to one and not the other is how a preview ends up stretched.
        listOf(DpSize(56.dp, 31.5.dp), DpSize(320.dp, 180.dp)).forEach { native ->
            listOf(200.dp, 360.dp, 4000.dp).forEach { bar ->
                val size: DpSize = previewFrameSize(bar, SHARE, FLOOR, native)
                val ratio: Float = size.height.value / size.width.value

                assertTrue(
                    kotlin.math.abs(ratio - TILE_ASPECT) < TOLERANCE,
                    "bar $bar, tile $native came out at $ratio rather than $TILE_ASPECT",
                )
            }
        }
    }
}

private const val TOLERANCE = 0.001f
