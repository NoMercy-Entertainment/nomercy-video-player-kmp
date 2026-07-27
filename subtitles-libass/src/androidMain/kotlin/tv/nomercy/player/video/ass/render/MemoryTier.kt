// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

// How much a device can afford to spend on subtitles.
//
// Android is the only platform where this matters and it matters enormously. A
// television application gets a heap measured in hundreds of megabytes for the
// whole process, and libass will happily fill it: the default cache is 128MB,
// which on a 248MB heap left so little room that garbage collection ran for
// eight and nine seconds at a time and the player was declared stuck.
//
// So the budget is chosen from the heap rather than assumed, and every number
// here is one that was measured on hardware rather than picked.
public enum class MemoryTier(
    public val bitmapPoolBytes: Int,
    public val glyphCacheMax: Int,
    public val libassCacheMegabytes: Int,
    public val maxRenderWidth: Int,
    public val maxRenderHeight: Int,
) {

    // Television boxes with a 256MB heap. Rendering above 720p on one of these
    // spends more on the subtitle bitmap than on the video frame under it.
    LOW(
        bitmapPoolBytes = 14 * BYTES_PER_MEGABYTE,
        glyphCacheMax = 2_500,
        libassCacheMegabytes = 8,
        maxRenderWidth = 1_280,
        maxRenderHeight = 720,
    ),

    MEDIUM(
        bitmapPoolBytes = 20 * BYTES_PER_MEGABYTE,
        glyphCacheMax = 4_000,
        libassCacheMegabytes = 16,
        maxRenderWidth = 1_920,
        maxRenderHeight = 1_080,
    ),

    HIGH(
        bitmapPoolBytes = 24 * BYTES_PER_MEGABYTE,
        glyphCacheMax = 6_000,
        libassCacheMegabytes = 32,
        maxRenderWidth = 1_920,
        maxRenderHeight = 1_080,
    ),
    ;

    // Roughly how many glyphs the megabyte budget could hold on its own.
    //
    // The count cap and the megabyte cap are two limits on one cache, and the
    // count is the one that bites first. Raising the megabytes without raising
    // the count means the cache evicts on count while still well inside its
    // memory budget, and every eviction sends FreeType back to re-rasterize a
    // glyph it already had — one such mismatch cost 1834ms of frame time.
    public val glyphsTheBudgetCouldHold: Int
        get() = libassCacheMegabytes * BYTES_PER_MEGABYTE / BYTES_PER_GLYPH

    public companion object {

        // The heap the system granted this process, which is what
        // ActivityManager.getMemoryClass reports.
        public fun forHeapMegabytes(heapMegabytes: Int): MemoryTier = when {
            heapMegabytes <= LOW_HEAP_CEILING -> LOW
            heapMegabytes <= MEDIUM_HEAP_CEILING -> MEDIUM
            else -> HIGH
        }
    }
}

private const val BYTES_PER_MEGABYTE = 1024 * 1024

// A rasterized glyph, measured rather than derived. Two kilobytes covers a
// typical subtitle face at television sizes with its outline and shadow.
private const val BYTES_PER_GLYPH = 2_048

// A 256MB television reports 256 or a little under it. A phone from the same
// era reports 192 and belongs in the same tier — it has the same problem.
private const val LOW_HEAP_CEILING = 256

private const val MEDIUM_HEAP_CEILING = 384
