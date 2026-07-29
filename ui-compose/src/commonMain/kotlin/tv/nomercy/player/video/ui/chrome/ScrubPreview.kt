// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.nomercy.player.video.tv.formatTime

/**
 * What the web calls `.slider-pop`: the frame under the viewer's thumb, the
 * time it lands on, and the chapter it falls in.
 *
 * The library had the whole apparatus for this and drew none of it. A sprite
 * sheet was parsed, a frame was looked up, and the answer was handed out through
 * an `onPreview` callback that the chrome's own assembly left empty — so a
 * viewer dragging along the bar on a phone saw a number move and no picture,
 * while the same drag in a browser showed the scene they were hunting for.
 *
 * Drawn here rather than left to the host for the same reason the bar is: a
 * consumer that had to build this would be building the part of a player that is
 * hardest to get right and least specific to them.
 */
@Composable
public fun ScrubPreview(
    seconds: Double,
    /** Where along the bar the thumb is, 0 to 1. The bubble centres on it. */
    fraction: Float,
    /** Bar width, so the bubble can be centred and kept inside the picture. */
    barWidth: Dp,
    modifier: Modifier = Modifier,
    frame: ImageBitmap? = null,
    /** Size the sheet DECLARES, so the box does not resize when pixels land. */
    frameSize: androidx.compose.ui.unit.DpSize? = null,
    chapterTitle: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(POP_GAP),
        modifier = modifier
            .centeredOn(fraction, barWidth)
            .background(POP_BACKGROUND, RoundedCornerShape(POP_RADIUS))
            .padding(bottom = POP_BOTTOM_PADDING)
            .testTag(SCRUB_PREVIEW_TAG),
    ) {
        // Sized before the pixels arrive, from what the sheet says a frame
        // measures. A box that grows when the image lands is a box that jumps
        // under the thumb that is dragging it.
        frameSize?.let { declared ->
            Box(modifier = Modifier.size(declared)) {
                frame?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier.size(declared).testTag(SCRUB_FRAME_TAG),
                    )
                }
            }
        }

        // Monospace, as the web sets it. A clock in a proportional face shifts
        // its own width as the digits change, so the bubble twitches while a
        // viewer is trying to read it.
        BasicText(
            text = formatTime(seconds),
            style = POP_TEXT,
            modifier = Modifier.padding(horizontal = TEXT_INSET).testTag(SCRUB_TIME_TAG),
        )

        // `.chapter-text`, and absent when the item has no chapters rather than
        // blank: an empty line under the clock reads as a title that failed.
        chapterTitle?.takeIf { it.isNotBlank() }?.let { title ->
            BasicText(
                text = title,
                style = CHAPTER_TEXT,
                modifier = Modifier.padding(horizontal = TEXT_INSET).testTag(SCRUB_CHAPTER_TAG),
            )
        }
    }
}

/**
 * The white dot on the bar, which the web calls `.slider-nipple`.
 *
 * Sixteen units across on an eight-unit bar, so it stands proud of it on both
 * sides — that overhang is what makes the position readable at a glance rather
 * than something a viewer has to look for.
 */
@Composable
public fun ScrubNipple(fraction: Float, barWidth: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .centeredOn(fraction, barWidth)
            .size(NIPPLE_SIZE)
            .background(Color.White, CircleShape)
            .testTag(SCRUB_NIPPLE_TAG),
    )
}

/**
 * `transform: translateX(-50%)` with the clamping the web's own positioning
 * does: centred on the thumb, and never hanging off either end of the bar.
 *
 * A layout modifier rather than an offset because the width is not known until
 * the child has measured itself, and a bubble placed before it knows how wide it
 * is centres on the wrong point by half its own width.
 */
private fun Modifier.centeredOn(fraction: Float, barWidth: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val bar: Float = barWidth.toPx()
    val centre: Float = bar * fraction.coerceIn(0f, 1f)
    val left: Float = (centre - placeable.width / 2f).coerceIn(0f, (bar - placeable.width).coerceAtLeast(0f))

    layout(placeable.width, placeable.height) { placeable.placeRelative(left.toInt(), 0) }
}

internal const val SCRUB_PREVIEW_TAG = "nm-scrub-preview"
internal const val SCRUB_FRAME_TAG = "nm-scrub-frame"
internal const val SCRUB_TIME_TAG = "nm-scrub-time"
internal const val SCRUB_CHAPTER_TAG = "nm-scrub-chapter"
internal const val SCRUB_NIPPLE_TAG = "nm-scrub-nipple"

// `background: rgba(20, 20, 25, 0.95)` — not black. The blue cast is what keeps
// the bubble distinct from the letterboxing behind it on a dark scene.
private val POP_BACKGROUND = Color(red = 20, green = 20, blue = 25, alpha = 242)

// `border-radius: 6px`, `gap: 4px`, `padding-bottom: 4px`, `padding: 0 8px`.
private val POP_RADIUS = 6.dp
private val POP_GAP = 4.dp
private val POP_BOTTOM_PADDING = 4.dp
private val TEXT_INSET = 8.dp

// `width: 16px; height: 16px`.
private val NIPPLE_SIZE = 16.dp

// The browser root is sixteen pixels, so the arithmetic is written out rather
// than the answer, exactly as the top bar's sizes are.
private const val REM = 16f

private val POP_TEXT = TextStyle(
    color = Color.White,
    fontSize = (0.78f * REM).sp,
    fontFamily = FontFamily.Monospace,
    textAlign = TextAlign.Center,
)

private val CHAPTER_TEXT = TextStyle(
    color = Color.White.copy(alpha = 0.75f),
    fontSize = (0.72f * REM).sp,
    textAlign = TextAlign.Center,
)
