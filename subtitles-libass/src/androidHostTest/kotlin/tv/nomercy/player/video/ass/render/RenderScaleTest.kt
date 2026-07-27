// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// What a device is allowed to rasterize subtitles at.
class RenderScaleTest {

    @Test
    fun aTelevisionBoxDrawsA1080pSurfaceAt720p() {
        // The case the tier exists for. Two thirds on each axis is four ninths
        // of the pixels, which is the difference between a subtitle overlay that
        // fits beside the video frame and one that does not.
        val scale: Double = renderScaleFor(1_920, 1_080, MemoryTier.LOW)

        assertEquals(720.0 / 1_080.0, scale, 0.0001)
    }

    @Test
    fun aDeviceWithRoomDrawsAtFullSize() {
        assertEquals(1.0, renderScaleFor(1_920, 1_080, MemoryTier.HIGH))
    }

    @Test
    fun theSurfaceKeepsItsShape() {
        // One factor for both axes. Clamping width and height separately makes
        // an ultrawide surface render its subtitles squashed, which is worse
        // than rendering them small.
        val ultrawide: Double = renderScaleFor(3_440, 1_440, MemoryTier.LOW)
        val renderedWidth: Int = (3_440 * ultrawide).toInt()
        val renderedHeight: Int = (1_440 * ultrawide).toInt()

        assertEquals(
            3_440.0 / 1_440.0,
            renderedWidth.toDouble() / renderedHeight,
            0.01,
            "the overlay came out a different shape from the surface",
        )
    }

    @Test
    fun aSurfaceSmallerThanTheCeilingIsNotEnlarged() {
        // Rendering above the surface costs memory to produce detail that is
        // then thrown away on the way down, and memory is the whole problem.
        assertEquals(1.0, renderScaleFor(640, 360, MemoryTier.LOW))
    }

    @Test
    fun everyTierStaysInsideItsOwnCeiling() {
        for (tier in MemoryTier.entries) {
            val scale: Double = renderScaleFor(3_840, 2_160, tier)

            assertTrue((3_840 * scale) <= tier.maxRenderWidth + 1, "$tier rendered too wide")
            assertTrue((2_160 * scale) <= tier.maxRenderHeight + 1, "$tier rendered too tall")
        }
    }

    @Test
    fun aPortraitSurfaceIsClampedByItsHeight() {
        // A phone held upright. Width is already inside the ceiling and height
        // is nowhere near it, so a clamp that only looked at width would render
        // a 1920-tall overlay on a device that cannot hold one.
        val scale: Double = renderScaleFor(1_080, 1_920, MemoryTier.LOW)

        assertEquals(720.0 / 1_920.0, scale, 0.0001)
        assertTrue((1_920 * scale) <= MemoryTier.LOW.maxRenderHeight + 1)
    }

    @Test
    fun aSurfaceWithNoSizeYetIsLeftAlone() {
        // The first frames arrive before the view has been measured. The ceiling
        // is what answers this: dividing by a zero edge gives an infinity, not a
        // not-a-number, and an infinity loses to the one.
        assertEquals(1.0, renderScaleFor(0, 0, MemoryTier.LOW))
        assertEquals(1.0, renderScaleFor(1_920, 0, MemoryTier.LOW))
    }
}
