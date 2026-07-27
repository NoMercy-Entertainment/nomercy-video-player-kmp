// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.nomercy.player.core.cues.SpriteCue

public actual fun spriteTileSource(
    encodedSheet: ByteArray,
    frames: List<SpriteCue>,
    scope: CoroutineScope,
): SpriteTileSource? = BandedSpriteTileSource(encodedSheet, frames, scope)

// Reads preview frames out of a sheet a grid row at a time.
//
// The sheet lays frames out left to right, wrapping to the next row, and the
// scrub strip walks them in that same order — so one horizontal band covers a
// long run of consecutive frames. Decoding a band and slicing every frame out of
// it costs one read for the whole run, and the band is the sheet's width by a
// single frame's height: a few megabytes against the hundred-plus the whole
// sheet would take.
//
// That is the entire reason this exists. Decoding the sheet whole forced a
// downscale to stay inside a sane memory budget, and the downscale applied to
// every frame in it, so the preview was soft regardless of what the server
// rendered.
private class BandedSpriteTileSource(
    private val encodedSheet: ByteArray,
    private val frames: List<SpriteCue>,
    private val scope: CoroutineScope,
) : SpriteTileSource {

    // Observable so a frame arriving mid-scrub recomposes whatever is drawing
    // the strip. A draw pass that misses returns null and paints nothing; the
    // band lands a moment later.
    private val decoded = mutableStateMapOf<Int, ImageBitmap>()
    private val requestedBands = mutableSetOf<Int>()
    private val decodeLock = Mutex()

    private var decoder: BitmapRegionDecoder? = null
    private var released = false

    // Frame indices grouped by the band they sit in, so one decode fills all.
    private val bandMembers: Map<Int, List<Int>> = frames.indices.groupBy { frames[it].y }

    override fun frame(index: Int): ImageBitmap? {
        decoded[index]?.let { return it }

        val band: Int = frames.getOrNull(index)?.y ?: return null
        requestBand(band)

        // Pull the next band in too. The strip is walked in order, so by the
        // time the viewer reaches the end of this row the following one is
        // already sliced and waiting.
        frames.getOrNull(index + (bandMembers[band]?.size ?: 0))?.let { requestBand(it.y) }

        return null
    }

    override fun release() {
        released = true
        decoded.clear()
        requestedBands.clear()
        runCatching { decoder?.recycle() }
        decoder = null
    }

    private fun requestBand(bandTop: Int) {
        if (released || !requestedBands.add(bandTop)) return

        scope.launch(Dispatchers.IO) {
            decodeLock.withLock {
                if (!released) runCatching { decodeBand(bandTop) }
            }
        }
    }

    private fun decodeBand(bandTop: Int) {
        val members: List<Int> = bandMembers[bandTop] ?: return
        val band: Bitmap = readBand(bandTop, members) ?: return

        for (index in members) {
            if (released) break
            sliceOf(band, frames[index])?.let { decoded[index] = it }
        }

        band.recycle()
    }

    private fun readBand(bandTop: Int, members: List<Int>): Bitmap? {
        val reader: BitmapRegionDecoder = decoder ?: openDecoder() ?: return null

        // The band is as tall as the tallest frame in the row and as wide as the
        // furthest one reaches, clamped to the sheet. Clamped rather than
        // trusted: a cue describing a region past the edge means the sheet and
        // the VTT disagree, which happens when one is regenerated and the other
        // is still cached.
        val bandHeight: Int = members.maxOf { frames[it].height }
        val bandRight: Int = members.maxOf { frames[it].x + frames[it].width }

        return reader.decodeRegion(
            Rect(
                0,
                bandTop,
                bandRight.coerceAtMost(reader.width),
                (bandTop + bandHeight).coerceAtMost(reader.height),
            ),
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        )
    }

    private fun sliceOf(band: Bitmap, cue: SpriteCue): ImageBitmap? {
        val left: Int = cue.x.coerceIn(0, band.width)
        val width: Int = cue.width.coerceAtMost(band.width - left)
        val height: Int = cue.height.coerceAtMost(band.height)
        if (width <= 0 || height <= 0) return null

        return Bitmap.createBitmap(band, left, 0, width, height).asImageBitmap()
    }

    private fun openDecoder(): BitmapRegionDecoder? =
        runCatching {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(encodedSheet, 0, encodedSheet.size, false)
        }
            .getOrNull()
            ?.also { decoder = it }
}
