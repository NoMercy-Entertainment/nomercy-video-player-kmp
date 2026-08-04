// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.compositeAssFrame
import kotlin.coroutines.coroutineContext

/**
 * Draws what libass rasterized.
 *
 * ASS is not text with a colour — it is positioned, rotated, faded,
 * karaoke-timed drawing with its own layout engine — so the cue arrives as
 * images with positions rather than as a string a chrome can style. This lays
 * them over the video at the size the video is actually being drawn at, which
 * is why it takes the surface's size from layout rather than from the stream:
 * a sign pinned to a character's shirt is in the wrong place the moment those
 * two disagree.
 *
 * [positionMs] is read on every poll rather than passed as a value, so the
 * layer does not recompose sixty times a second to follow the playhead.
 *
 * [contentDescription] is for the consumer that has the plain-text line as
 * well. Nothing here can read the picture back into words, and a screen reader
 * meeting an undescribed image gets nothing at all.
 */
@Composable
public fun AssSubtitleLayer(
    renderer: AssRenderer,
    positionMs: () -> Long,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    var surface: IntSize by remember { mutableStateOf(IntSize.Zero) }
    var picture: ImageBitmap? by remember { mutableStateOf(null) }

    LaunchedEffect(renderer, surface) {
        if (surface.width > 0 && surface.height > 0) {
            renderer.frameSize(surface.width, surface.height)
            while (coroutineContext.isActive) {
                picture = nextPicture(renderer, positionMs(), surface) ?: picture
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .testTag(ASS_SUBTITLE_TAG)
            .onSizeChanged { measured: IntSize -> surface = measured },
    ) {
        picture?.let { frame: ImageBitmap ->
            Image(
                bitmap = frame,
                contentDescription = contentDescription,
                contentScale = ContentScale.None,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// Null means "keep what is on screen". The renderer answers null for a frame
// that has not changed, which is most of them — a static line held for four
// seconds is one render and ninety-five identical ones nobody should pay for.
private fun nextPicture(renderer: AssRenderer, timeMs: Long, surface: IntSize): ImageBitmap? {
    val frame: AssFrame = renderer.render(timeMs) ?: return null
    if (!frame.changed) return null

    val pixels: IntArray = compositeAssFrame(frame.images, surface.width, surface.height)
    return assImageBitmap(pixels, surface.width, surface.height)
}

// Straight-alpha ARGB pixels as the toolkit's own bitmap. Every platform has
// one and none of them is reachable from common code.
internal expect fun assImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap

// The cadence a karaoke wipe needs to look continuous, and the one
// RenderScheduler already uses for a moving cue. A static line costs nothing at
// this rate because the renderer answers null for it.
private const val POLL_INTERVAL_MS = 42L

internal const val ASS_SUBTITLE_TAG = "nm-ass-subtitles"
