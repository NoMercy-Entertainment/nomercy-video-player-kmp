// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
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
            .centeredOn(fraction, barWidth, liftedAbove = POP_LIFT)
            // `min-width: 60px`, so a bubble on an item with no sprite sheet is
            // still a card with a clock in it rather than a strip the width of
            // four digits.
            .widthIn(min = POP_MIN_WIDTH)
            .background(POP_BACKGROUND, RoundedCornerShape(POP_RADIUS))
            .padding(bottom = POP_BOTTOM_PADDING)
            .testTag(SCRUB_PREVIEW_TAG),
    ) {
        // Sized before the pixels arrive, from what the sheet says a frame
        // measures. A box that grows when the image lands is a box that jumps
        // under the thumb that is dragging it.
        frameSize?.let { declared -> PopFrame(declared, frame) }

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
 * The still, inset in the card.
 *
 * `margin: 6px 6px 0` with `border-radius: 3px` — the frame sits INSIDE the card
 * with a band of it showing on three sides, and its own corners are rounded half
 * as much as the card's.
 *
 * Neither was drawn. The image went edge to edge and square into a rounded card,
 * so its top corners were clipped square by the card's radius and there was no
 * border at all — which is the whole reason the bubble reads as a framed still in
 * a browser and read as a bare tile here.
 */
@Composable
private fun PopFrame(declared: androidx.compose.ui.unit.DpSize, frame: ImageBitmap?) {
    Box(
        modifier = Modifier
            .padding(start = FRAME_INSET, top = FRAME_INSET, end = FRAME_INSET)
            .clip(RoundedCornerShape(FRAME_RADIUS))
            .size(declared),
    ) {
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(declared).testTag(SCRUB_FRAME_TAG),
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
public fun ScrubNipple(
    fraction: Float,
    barWidth: Dp,
    modifier: Modifier = Modifier,
    /**
     * Grown to 24 units, which is `.slider-bar:hover .slider-nipple` and
     * `.slider-bar.slider-scrubbing .slider-nipple` — a pointer on the bar or a
     * drag under way. The stylesheet states both sizes and this drew only the
     * first, so the handle looked the same whether or not it was being held.
     */
    grown: Boolean = false,
) {
    Box(
        modifier = modifier
            .centeredOn(fraction, barWidth)
            .size(if (grown) NIPPLE_SIZE_GROWN else NIPPLE_SIZE)
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
 *
 * [liftedAbove] is `position: absolute; bottom: Npx`: the child takes no space in
 * the row and hangs that far above it.
 */
private fun Modifier.centeredOn(
    fraction: Float,
    barWidth: Dp,
    liftedAbove: Dp = Dp.Unspecified,
): Modifier = layout { measurable, constraints ->
    // Against the bar's WIDTH and nothing else.
    //
    // The row this sits in is eight units tall — the height of the drawn bar —
    // and a Box of a fixed height caps every child measured against it. Handed
    // the row's constraints the bubble came out FOUR units tall: the frame, the
    // clock and the chapter name all coerced into a sliver, present to every
    // assertion that asked whether it existed and invisible to the viewer it was
    // drawn for. The scrubber beside it escapes the same cap with
    // `requiredHeight`; this is that escape, expressed where the overflow is.
    val placeable: Placeable = measurable.measure(Constraints(maxWidth = constraints.maxWidth))
    val bar: Float = barWidth.toPx()
    val centre: Float = bar * fraction.coerceIn(0f, 1f)
    val left: Float = (centre - placeable.width / 2f).coerceIn(0f, (bar - placeable.width).coerceAtLeast(0f))

    if (liftedAbove.isUnspecified) {
        layout(placeable.width, placeable.height) { placeable.placeRelative(left.toInt(), 0) }
    } else {
        // Zero-sized, so the row keeps the height the browser gives it, and the
        // child placed off the top of it. The origin is the bar's centre, which
        // is where an empty child lands in a centre-aligned row.
        layout(0, 0) { placeable.placeRelative(left.toInt(), -(placeable.height + liftedAbove.roundToPx())) }
    }
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

// `.slider-pop { bottom: 24px }`, measured from the bottom of an 8px row. The
// origin here is the row's CENTRE, which is four of those units higher.
private val POP_LIFT = 20.dp
private val POP_GAP = 4.dp
private val POP_BOTTOM_PADDING = 4.dp
private val TEXT_INSET = 8.dp

// `.slider-pop { min-width: 60px }`.
private val POP_MIN_WIDTH = 60.dp

// `.slider-pop-image[style*='background-image'] { margin: 6px 6px 0; border-radius: 3px }`.
private val FRAME_INSET = 6.dp
private val FRAME_RADIUS = 3.dp

// `width: 16px; height: 16px`, and `24px` once a pointer is on the bar or a drag
// is under way.
private val NIPPLE_SIZE = 16.dp
private val NIPPLE_SIZE_GROWN = 24.dp

// `.slider-pop`'s `transition: opacity 0.12s ease`, with `ease` written out as the
// curve it is: `cubic-bezier(0.25, 0.1, 0.25, 1)`. The bubble was mounted and
// unmounted outright, so a pointer sweeping across the bar snapped it on and off
// at every entry and exit.
internal const val POP_FADE_MS: Int = 120
internal val POP_EASE: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

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
