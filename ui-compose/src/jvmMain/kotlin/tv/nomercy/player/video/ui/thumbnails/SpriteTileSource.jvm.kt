// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import tv.nomercy.player.core.cues.SpriteCue

// Skia rather than ImageIO, because sheets are webp and a stock JDK has no
// reader for it — ImageIO returns null and the preview is silently blank.
public actual fun spriteTileSource(
    encodedSheet: ByteArray,
    frames: List<SpriteCue>,
    scope: CoroutineScope,
): SpriteTileSource? =
    runCatching { Image.makeFromEncoded(encodedSheet) }
        .getOrNull()
        ?.let { SkiaSpriteTileSource(it, frames) }

// A desktop holds the whole sheet without complaint, so this decodes once and
// slices on demand rather than banding.
//
// The Android limit being worked around there is a heap measured in hundreds of
// megabytes on a television, and it does not exist here. What does carry over is
// the rule that produced it: the sheet is read at its stored resolution and
// never scaled down, because scaling the sheet scales every frame in it and the
// preview goes soft for the whole title.
private class SkiaSpriteTileSource(
    private val sheet: Image,
    private val frames: List<SpriteCue>,
) : SpriteTileSource {

    private val decoded: MutableMap<Int, ImageBitmap> = mutableMapOf()
    private var released = false

    override fun frame(index: Int): ImageBitmap? {
        if (released) return null

        return decoded[index] ?: sliceOf(index)?.also { decoded[index] = it }
    }

    private fun sliceOf(index: Int): ImageBitmap? {
        val cue: SpriteCue = frames.getOrNull(index) ?: return null

        // Clamped against the sheet rather than trusted. A cue describing a
        // region past the edge is a sheet and a VTT that disagree, which happens
        // when one of the two is regenerated and the other is cached.
        val left: Int = cue.x.coerceIn(0, sheet.width)
        val top: Int = cue.y.coerceIn(0, sheet.height)
        val width: Int = cue.width.coerceAtMost(sheet.width - left)
        val height: Int = cue.height.coerceAtMost(sheet.height - top)
        if (width <= 0 || height <= 0) return null

        val surface: Surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawImageRect(
            sheet,
            Rect.makeXYWH(left.toFloat(), top.toFloat(), width.toFloat(), height.toFloat()),
            Rect.makeWH(width.toFloat(), height.toFloat()),
        )

        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    override fun release() {
        released = true
        decoded.clear()
        runCatching { sheet.close() }
    }
}
