// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * A control's label, on hover, positioned the way the web positions it.
 *
 * ChromeTooltip had the arithmetic and its tests, and nothing drew anything: a
 * pointer resting on a button here said nothing while the same button on the web
 * named itself. Geometry with no renderer is the same defect as a sprite with no
 * consumer, and it read as done for the same reason — the file existed.
 *
 * Every number is one declaration in `.tooltip`:
 *
 *  - `bottom: calc(100% + 8px)` — [GAP] above the control, not overlapping it.
 *  - `background: rgba(20, 22, 30, 0.92)`, `border-radius: 8px`.
 *  - `font-size: 0.75rem` with `line-height: 2`, so the box is taller than the
 *    text and the padding is not doing that work alone.
 *  - `padding: 8px 16px`, `white-space: nowrap`.
 *  - `transition: opacity 0.15s` — it fades in, and a label that snaps on reads
 *    as a different component appearing.
 *
 * The arrow is `.tooltip::after`: a [ARROW_SIZE] triangle that keeps pointing at
 * the button while the label itself slides away from centre near the bar's edges.
 */
@Composable
public fun ControlTooltip(
    text: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    // Nothing measured, nothing to clamp — and a Box with no content still takes
    // part in layout, which would push the control it belongs to.
    if (text.isEmpty()) return

    val opacity: Float by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = FADE_MS),
        label = "tooltip",
    )

    if (opacity <= 0f) return

    Box(modifier = modifier.alpha(opacity).aboveAnchor(GAP)) {
        Box(
            modifier = Modifier
                .background(BACKGROUND, RoundedCornerShape(RADIUS))
                .padding(horizontal = PADDING_HORIZONTAL, vertical = PADDING_VERTICAL),
        ) {
            BasicText(
                text = text,
                style = TextStyle(color = Color.White, fontSize = TEXT_SIZE, lineHeight = TEXT_LINE_HEIGHT),
                // `white-space: nowrap`. A label that wraps changes the box's
                // width, and the clamp that keeps it on screen is computed from
                // that width.
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * Whether a control has been hovered long enough to name itself.
 *
 * [ChromeTooltip.DELAY_MS] of hovering, dismissed on leave AND on press: a
 * viewer who has clicked the button has their answer, and the web hides it on
 * click for that reason.
 *
 * Reads the same [InteractionSource] the button already builds for its own
 * pressed and focused states, so attaching a tooltip costs a caller one
 * parameter rather than its own pointer handling.
 */
@Composable
public fun rememberTooltipVisible(interactions: InteractionSource): Boolean {
    var hovered: Boolean by remember { mutableStateOf(false) }
    var shown: Boolean by remember { mutableStateOf(false) }

    LaunchedEffect(interactions) {
        interactions.interactions.collect { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> hovered = true
                is HoverInteraction.Exit -> {
                    hovered = false
                    shown = false
                }
                // Both halves. Collecting only Press leaves the label hidden for
                // good after the first click, because nothing sets it back.
                is PressInteraction.Press -> shown = false
                is PressInteraction.Release -> shown = false
                else -> Unit
            }
        }
    }

    LaunchedEffect(hovered) {
        if (hovered) {
            delay(ChromeTooltip.DELAY_MS)
            shown = hovered
        }
    }

    return shown
}

/**
 * Draw above the thing this is anchored to, clamped inside the parent.
 *
 * `position: absolute; bottom: calc(100% + 8px)` in one modifier: it reports no
 * size of its own, so the control it decorates keeps its place in the row, and
 * it uses [ChromeTooltip.leftFor] for the horizontal placement — which is what
 * that arithmetic was written for and never called with.
 */
internal fun Modifier.aboveAnchor(gap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))

    // Zero, so the row lays out as though this were not here. A tooltip that
    // occupied space would widen the bar every time a pointer crossed it.
    layout(width = 0, height = 0) {
        val anchorCentre: Float = constraints.maxWidth / 2f
        val left: Float = ChromeTooltip.leftFor(
            buttonCenter = anchorCentre,
            tooltipWidth = placeable.width.toFloat(),
            boundsLeft = 0f,
            boundsRight = constraints.maxWidth.toFloat(),
        )

        placeable.place(x = left.toInt() - anchorCentre.toInt(), y = -placeable.height - gap.roundToPx())
    }
}

private val GAP: Dp = 8.dp
private val RADIUS: Dp = 8.dp
private val PADDING_HORIZONTAL: Dp = 16.dp
private val PADDING_VERTICAL: Dp = 8.dp

// `rgba(20, 22, 30, 0.92)`.
private val BACKGROUND: Color = Color(red = 20, green = 22, blue = 30, alpha = 235)

// 0.75rem against the same 16px root the rest of the chrome reads.
private val TEXT_SIZE = 12.sp

// `line-height: 2`.
private val TEXT_LINE_HEIGHT = 24.sp

private const val FADE_MS = 150
internal val ARROW_SIZE: Dp = 5.dp
