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

// The subtitle memory budget, and the one relationship in it that is easy to
// break by hand.
//
// These are measured numbers rather than derived ones, so most of what can be
// asserted about them is that they move together. That is exactly the failure
// worth catching: raising a cache's megabytes without raising its glyph count
// makes the count the binding limit, and the cache then evicts while well inside
// its memory budget — sending FreeType back to re-rasterize glyphs it already
// had, for 1834ms of frame time.
class MemoryTierTest {

    @Test
    fun aTelevisionSizedHeapGetsTheSmallestBudget() {
        // The case all of this exists for. A 256MB heap with libass on its
        // default 128MB cache is where the eight-second collections came from.
        assertEquals(MemoryTier.LOW, MemoryTier.forHeapMegabytes(256))
        assertEquals(MemoryTier.LOW, MemoryTier.forHeapMegabytes(192))
    }

    @Test
    fun aModernPhoneGetsTheLargestBudget() {
        assertEquals(MemoryTier.HIGH, MemoryTier.forHeapMegabytes(512))
    }

    @Test
    fun everyTierStaysUnderTheMemoryItsCountImplies() {
        // The count cap has to be reachable within the megabyte budget. A cap
        // above what the budget could hold means the megabytes evict first and
        // the count is decoration.
        for (tier in MemoryTier.entries) {
            assertTrue(
                tier.glyphCacheMax <= tier.glyphsTheBudgetCouldHold,
                "$tier caps at ${tier.glyphCacheMax} glyphs in a budget holding " +
                    "${tier.glyphsTheBudgetCouldHold}",
            )
        }
    }

    @Test
    fun aBiggerCacheAlsoGetsMoreGlyphs() {
        // The relationship the note is about. Bumping one number and not the
        // other is the regression, and it is invisible until a frame takes most
        // of two seconds.
        val byBudget: List<MemoryTier> = MemoryTier.entries.sortedBy { it.libassCacheMegabytes }

        byBudget.zipWithNext { smaller, larger ->
            assertTrue(
                larger.glyphCacheMax > smaller.glyphCacheMax,
                "$larger has a bigger cache than $smaller but no more glyphs in it",
            )
        }
    }

    @Test
    fun theSmallestTierAlsoRendersSmaller() {
        // Not only the caches. A 256MB box rendering subtitles at 1080p spends
        // more on the overlay than on the video frame beneath it.
        assertTrue(MemoryTier.LOW.maxRenderWidth < MemoryTier.MEDIUM.maxRenderWidth)
        assertTrue(MemoryTier.LOW.maxRenderHeight < MemoryTier.MEDIUM.maxRenderHeight)
    }

    @Test
    fun noTierAsksForMoreThanAQuarterOfATelevisionHeap() {
        // A pool plus a cache is what subtitles cost before a single frame is
        // decoded, and the video underneath needs the rest.
        for (tier in MemoryTier.entries) {
            val megabytes: Int = tier.bitmapPoolBytes / (1024 * 1024) + tier.libassCacheMegabytes

            assertTrue(megabytes <= 64, "$tier asks for ${megabytes}MB before anything is drawn")
        }
    }
}
