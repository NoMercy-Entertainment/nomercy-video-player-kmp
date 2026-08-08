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
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import tv.nomercy.player.video.subtitles.AssFrameCompositor
import tv.nomercy.player.video.subtitles.AssSurfaceFrame
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Draws what libass rasterized.
 *
 * ASS is not text with a colour — it is positioned, rotated, faded,
 * karaoke-timed drawing with its own layout engine — so the cue arrives as
 * images with positions rather than as a string a chrome can style.
 *
 * Rasterized at the resolution the track was authored against and scaled to
 * the overlay, rather than rasterized at the overlay's own size. Drawing
 * straight into a small overlay keeps the text proportional but not the
 * outlines, shadows and blur around it, and a cue in a windowed player comes
 * out thick and blobby with nothing reporting a problem.
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
    var frameClock: TimeSource.Monotonic.ValueTimeMark = remember { TimeSource.Monotonic.markNow() }
    var surface: IntSize by remember { mutableStateOf(IntSize.Zero) }
    var frame: ImageBitmap? by remember { mutableStateOf(null) }

    // One compositor for the life of the layer, holding the buffers it draws
    // into. A fresh eight-megabyte surface per frame was costing more than
    // libass spent rasterising the glyphs that go on it.
    val drawing: AssDrawing = remember { AssDrawing(AssFrameCompositor(), AssPictureSurface()) }


    LaunchedEffect(renderer, surface) {
        if (surface.width > 0 && surface.height > 0) {
            var target: IntSize = IntSize.Zero

            while (coroutineContext.isActive) {
                // Re-asked rather than set once, because the size to draw at is
                // the TRACK's, and the track arrives after the layer does. A
                // frame size fixed on the first composition is the overlay's
                // size forever, and the overlay is usually smaller than the
                // video.
                val next: IntSize = rasterSize(renderer.storageSize(), surface)
                if (next != target) {
                    target = next
                    // Sizing is native work too, and it happens on every resize.
                    withContext(Dispatchers.Default) { renderer.frameSize(target.width, target.height) }
                }

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
                val drawn: ImageBitmap? = withContext(Dispatchers.Default) {
                    nextPicture(renderer, drawing, positionMs(), target)
                }

                frame = drawn ?: frame

                // Paced by the display, not by a timer.
                //
                // This was delay(42), which is twenty-four updates a second no
                // matter what the screen is doing, and a subtitle that MOVES —
                // a karaoke wipe, a \move sign, a fade — steps rather than
                // slides at that rate. On a 120Hz panel it is one update per
                // five frames.
                //
                // withFrameNanos resumes on the frame the toolkit is about to
                // draw, so a moving cue advances once per drawn frame and a
                // still one costs nothing, because the renderer answers null
                // for a frame that has not changed. It also self-limits: if a
                // frame ever costs more than the budget the loop simply misses
                // the next callback instead of queueing work behind itself,
                // which a fixed delay does.
                //
                // Affordable now and not before. Compositing a frame of the
                // densest track measured 14ms when this was written and 3.5ms
                // after the compositor was rewritten; at 14ms per frame asking
                // for display rate would have been asking for a stall.
                // Display rate, with a floor of its own.
                //
                // withFrameNanos is supposed to pace this: resume on the frame
                // the toolkit is about to draw, so a moving cue advances once
                // per drawn frame and a still one costs nothing. On a window
                // that is not presenting it does not throttle at all — measured
                // at 500-600 iterations a second, each hopping to
                // Dispatchers.Default to rasterise a subtitle nobody can see
                // ten times per displayed frame. The process burned four and a
                // half cores and the UI thread starved: a window that responds,
                // paints nothing, and reports its playhead ticking hundreds of
                // times a second.
                //
                // So the loop keeps its own floor. Nothing is gained by
                // rasterising faster than a panel can show — 60Hz is already
                // more than a karaoke wipe needs — and a pacing primitive that
                // silently stops pacing must not be the only thing standing
                // between an overlay and every core on the machine.
                val elapsed: Long = frameClock.elapsedNow().inWholeMilliseconds
                if (elapsed < FRAME_FLOOR_MS) delay(FRAME_FLOOR_MS - elapsed)
                frameClock = TimeSource.Monotonic.markNow()

                withFrameNanos { }
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
                // Fit, not None. The picture is rasterized at the video's own
                // resolution and scaled here, which is what every other player
                // does and the only way an outline authored for 1080p stays
                // proportional in a smaller window.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// Null means "keep what is on screen". The renderer answers null for a frame
// that has not changed, which is most of them — a static line held for four
// seconds is one render and ninety-five identical ones nobody should pay for.
private suspend fun nextPicture(
    renderer: AssRenderer,
    drawing: AssDrawing,
    timeMs: Long,
    target: IntSize,
): ImageBitmap? {
    // Null from render() means "nothing changed", and the caller keeps what it
    // has — right for a still cue, wrong for no cue at all. One value carried
    // both answers, so a track that had been torn down left its last rasterised
    // frame painted forever: measured on the desktop testbed with Rail Wars'
    // lyrics sitting over No-Rin's opening while it loaded.
    //
    // hasTrack() is the third answer. No track composites to a transparent
    // frame, because the layer has no way to say "draw nothing" other than by
    // drawing nothing.
    val frame: AssFrame = renderer.render(timeMs)
        ?: return if (renderer.hasTrack()) null else blank(drawing, target)
    if (!frame.changed) return null

    // Banded, because a single ending sequence puts two hundred glyph runs over
    // an eighth of the screen and blending them in one pass was four
    // milliseconds — a quarter of a 60fps budget spent on subtitles alone.
    val composited: AssSurfaceFrame = drawing.compositor.renderParallel(frame.images, target.width, target.height)
    return drawing.picture.bitmap(composited, target.width, target.height)
}

// Nothing, drawn.
private suspend fun blank(drawing: AssDrawing, target: IntSize): ImageBitmap {
    val empty: AssSurfaceFrame = drawing.compositor.renderParallel(emptyList(), target.width, target.height)
    return drawing.picture.bitmap(empty, target.width, target.height)
}

// The two things that turn libass images into a bitmap, held together for the
// life of the layer: the compositor owns the pixel buffers and the surface owns
// the toolkit's copy of them, and neither is any use without the other.
private class AssDrawing(
    val compositor: AssFrameCompositor,
    val picture: AssPictureSurface,
)

// How big to rasterize, given what the track was authored against.
//
// The track's own space when there is one, so borders, shadows and blur come
// out at the proportions the author drew them at. Capped, because a 4K track in
// a thumbnail-sized overlay would rasterize eight million pixels a frame to be
// scaled into a few hundred thousand; the cap costs sharpness the display
// cannot show anyway.
//
// The SURFACE decides the raster, not the script.
//
// This used the script's own PlayRes — so a track authored at 1280x720 was
// rasterised at 1280x720 and then stretched over a 1080p pane, and the text
// came out soft. Reported as "the subtitle looks like 720p", which is exactly
// what it was.
//
// The mistake was reading PlayRes as a resolution. It is a COORDINATE SPACE:
// the numbers a script's positions, margins and font sizes are expressed in.
// libass takes that separately as the storage size and scales the layout onto
// whatever frame it is given, and glyphs are outlines — rasterising them larger
// produces real detail rather than invented detail, which is the opposite of
// what upscaling a bitmap does.
//
// So: storage stays the script's PlayRes (see NativeAssRenderer.applySize) and
// the frame is the surface, capped so a 4K pane does not cost eight million
// pixels a frame for a line of text.
internal fun rasterSize(source: AssSize?, surface: IntSize): IntSize {
    // Before the pane has been measured there is nothing to follow, so the
    // track's own space stands in — it is the only size known at that point.
    if (surface.width <= 0 || surface.height <= 0) {
        val space: AssSize = source?.takeIf { it.width > 0 && it.height > 0 } ?: return surface
        return IntSize(space.width, space.height)
    }

    val area: Long = surface.width.toLong() * surface.height.toLong()
    if (area <= MAX_RASTER_PIXELS) return surface

    val scale: Double = sqrt(MAX_RASTER_PIXELS.toDouble() / area.toDouble())
    return IntSize(
        (surface.width * scale).toInt().coerceAtLeast(1),
        (surface.height * scale).toInt().coerceAtLeast(1),
    )
}

// 1080p, which is Android's largest tier and the resolution these tracks are
// authored at.
private const val MAX_RASTER_PIXELS: Long = 1920L * 1080L

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

internal const val ASS_SUBTITLE_TAG = "nm-ass-subtitles"

// One frame at 60Hz. A cue that moves is smooth at this rate and a still one
// costs nothing, because the renderer answers null for a frame that has not
// changed.
private const val FRAME_FLOOR_MS = 16L
