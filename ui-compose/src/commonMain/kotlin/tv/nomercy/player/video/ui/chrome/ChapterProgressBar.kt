// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.media.Chapter
import tv.nomercy.player.video.chapters.ChapterMarker
import tv.nomercy.player.video.chapters.chapterFill
import tv.nomercy.player.video.chapters.chapterMarkers

// Everything the bar draws, which is more than a position.
//
// Grouped rather than passed one by one because they change together — a time
// update that did not also carry the buffer would draw a bar whose two halves
// disagree about how far the item has got.
public class ChapterBarState(
    public val currentSeconds: Double,
    public val duration: Double,
    public val bufferedFraction: Double = 0.0,
    public val chapters: List<Chapter> = emptyList(),
    public val hoverSeconds: Double? = null,
)

// The colours, so a chrome can theme the bar without forking it.
//
// The defaults are the stylesheet's, one for one: `.slider-bar` is white at
// 0.2, `.slider-buffer` at 0.4, `.slider-hover` at 0.3, and `.slider-progress`
// is `#fff` outright. Every one of these was a different number invented here,
// and the buffer was the one that mattered — drawn at 0.08 it is all but
// invisible, so a viewer on a slow line watched a bar that never showed how
// much had arrived and could not tell buffering from a stall.
//
// check-chrome-parity.py reads these alphas and the CSS and compares them.
public class ChapterBarColors(
    public val track: Color = Color.White.copy(alpha = 0.2f),
    public val segment: Color = Color.White.copy(alpha = 0.3f),
    public val buffer: Color = Color.White.copy(alpha = 0.4f),
    public val hover: Color = Color.White.copy(alpha = 0.3f),
    public val progress: Color = Color.White.copy(alpha = 1.0f),
)

// A scrubber divided into the item's chapters.
//
// Built on foundation rather than Material. A player library that pulls Material
// in makes every consumer take a design system with it, and the two chromes that
// draw this already have their own.
//
// The label carries the position rather than being a bare "seek bar", because a
// screen reader announcing only the control name leaves a viewer scrubbing blind.
@Composable
public fun ChapterProgressBar(
    state: ChapterBarState,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
    colors: ChapterBarColors = ChapterBarColors(),
    height: Dp = BAR_HEIGHT,
) {
    val markers: List<ChapterMarker> = chapterMarkers(state.chapters, state.duration)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = state.describe() }
            .pointerInput(state.duration) {
                detectTapGestures { at -> onSeek(state.secondsAt(at.x / size.width)) }
            }
            .pointerInput(state.duration) {
                detectHorizontalDragGestures { change, _ ->
                    onSeek(state.secondsAt(change.position.x / size.width))
                }
            },
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = colors.track, cornerRadius = radius)

        // Everything after this is a rectangle, and the ends of the bar are
        // round. Without the clip the buffer and the progress square them off.
        clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }) {
            drawSpan(colors.buffer, state.bufferedFraction)

            if (markers.isEmpty()) {
                drawSpan(colors.hover, state.hoverFraction())
                drawSpan(colors.progress, state.progressFraction())
            } else {
                drawSegments(markers, state, colors)
            }
        }
    }
}

private fun DrawScope.drawSegments(
    markers: List<ChapterMarker>,
    state: ChapterBarState,
    colors: ChapterBarColors,
) {
    val gap: Float = SEGMENT_GAP.toPx()

    for (marker in markers) {
        val left: Float = (marker.leftPercent / PERCENT).toFloat() * size.width
        val right: Float = (marker.rightPercent / PERCENT).toFloat() * size.width

        // The gap comes off the end of every segment but the last, so the bar
        // still reaches its own right edge. Taking it off that one too leaves a
        // sliver of track showing after the final chapter.
        val end: Float = if (marker === markers.last()) right else (right - gap).coerceAtLeast(left)
        val width: Float = end - left
        if (width <= 0f) continue

        drawRect(colors.segment, topLeft = Offset(left, 0f), size = Size(width, size.height))
        drawFill(colors.hover, left, width, chapterFill(marker, state.hoverPercent()))
        drawFill(colors.progress, left, width, chapterFill(marker, state.progressPercent()))
    }
}

private fun DrawScope.drawFill(color: Color, left: Float, width: Float, fraction: Double) {
    val filled: Float = width * fraction.toFloat()
    if (filled > 0f) {
        drawRect(color, topLeft = Offset(left, 0f), size = Size(filled, size.height))
    }
}

private fun DrawScope.drawSpan(color: Color, fraction: Double) {
    val width: Float = size.width * fraction.coerceIn(0.0, 1.0).toFloat()
    if (width > 0f) {
        drawRect(color, size = Size(width, size.height))
    }
}

private fun ChapterBarState.progressFraction(): Double =
    if (duration > 0.0) (currentSeconds / duration).coerceIn(0.0, 1.0) else 0.0

private fun ChapterBarState.hoverFraction(): Double =
    if (duration > 0.0) ((hoverSeconds ?: 0.0) / duration).coerceIn(0.0, 1.0) else 0.0

private fun ChapterBarState.progressPercent(): Double = progressFraction() * PERCENT

// Nought when nothing is hovered, so every segment fills to nothing rather than
// the first one filling to all of it.
private fun ChapterBarState.hoverPercent(): Double =
    if (hoverSeconds == null) 0.0 else hoverFraction() * PERCENT

private fun ChapterBarState.secondsAt(fraction: Float): Double =
    (fraction.toDouble().coerceIn(0.0, 1.0)) * duration

private fun ChapterBarState.describe(): String =
    "Seek bar, ${currentSeconds.toInt()} of ${duration.toInt()} seconds"

private const val PERCENT = 100.0
private val BAR_HEIGHT: Dp = 8.dp
private val SEGMENT_GAP: Dp = 4.dp
