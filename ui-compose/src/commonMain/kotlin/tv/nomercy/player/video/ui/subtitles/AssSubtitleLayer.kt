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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssFrameCompositor
import tv.nomercy.player.video.subtitles.AssSurfaceFrame
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
    var frame: ImageBitmap? by remember { mutableStateOf(null) }

    // One compositor for the life of the layer, holding the buffers it draws
    // into. A fresh eight-megabyte surface per frame was costing more than
    // libass spent rasterising the glyphs that go on it.
    val compositor: AssFrameCompositor = remember { AssFrameCompositor() }
    val picture: AssPictureSurface = remember { AssPictureSurface() }

    LaunchedEffect(renderer, surface) {
        if (surface.width > 0 && surface.height > 0) {
            // Sizing is native work too, and it happens on every resize.
            withContext(Dispatchers.Default) { renderer.frameSize(surface.width, surface.height) }

            while (coroutineContext.isActive) {
                // Rasterising OFF the composition thread, which is the whole
                // point of this line.
                //
                // A LaunchedEffect body runs on the main dispatcher, so this
                // loop was calling into libass — glyph shaping, blending and a
                // full-surface bitmap copy — on the thread Compose recomposes
                // and handles input on. Every poll blocked the frame it was
                // trying to draw into, which reads as a player that is slow and
                // sluggish everywhere, not as a subtitle layer that is
                // expensive. The scheduler's skipping already keeps the number
                // of renders low; where they run is a separate question and it
                // was answered wrong.
                //
                // Only the finished ImageBitmap crosses back, and the state
                // write lands on the main thread where composition expects it.
                val next: ImageBitmap? = withContext(Dispatchers.Default) {
                    nextPicture(renderer, compositor, picture, positionMs(), surface)
                }

                frame = next ?: frame
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
        frame?.let { drawn: ImageBitmap ->
            Image(
                bitmap = drawn,
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
private fun nextPicture(
    renderer: AssRenderer,
    compositor: AssFrameCompositor,
    picture: AssPictureSurface,
    timeMs: Long,
    surface: IntSize,
): ImageBitmap? {
    val frame: AssFrame = renderer.render(timeMs) ?: return null
    if (!frame.changed) return null

    val composited: AssSurfaceFrame = compositor.render(frame.images, surface.width, surface.height)
    return picture.bitmap(composited, surface.width, surface.height)
}

/**
 * Premultiplied ARGB pixels as the toolkit's own bitmap.
 *
 * A class rather than a function because the desktop has to keep something
 * between frames: Skia takes bytes and the compositor holds ints, and
 * converting the whole surface to find the band that changed cost more than
 * everything else in the frame together. Android keeps nothing and is a class
 * anyway, so the layer has one shape to hold rather than a function on one
 * platform and an object on the other.
 */
internal expect class AssPictureSurface() {
    fun bitmap(frame: AssSurfaceFrame, frameWidth: Int, frameHeight: Int): ImageBitmap
}

// The cadence a karaoke wipe needs to look continuous, and the one
// RenderScheduler already uses for a moving cue. A static line costs nothing at
// this rate because the renderer answers null for it.
private const val POLL_INTERVAL_MS = 42L

internal const val ASS_SUBTITLE_TAG = "nm-ass-subtitles"
