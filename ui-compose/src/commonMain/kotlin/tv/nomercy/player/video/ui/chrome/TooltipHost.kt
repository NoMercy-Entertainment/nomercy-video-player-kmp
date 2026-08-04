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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
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

    // A Popup, not a Box inside the control.
    //
    // Drawn as a child it was measured against the button's own 40dp, so
    // `white-space: nowrap` plus a 40dp ceiling clipped every label to one glyph —
    // "Dempen" rendered as "D". `.tooltip` is `position: absolute`: it escapes its
    // control, and a popup is what escapes a parent's constraints here.
    //
    // It also hands the position provider the anchor's bounds AND the window's
    // size, which is what ChromeTooltip.leftFor needs and never had. Clamping
    // against the button meant clamping into 40dp.
    Popup(popupPositionProvider = AboveAnchorPosition(GAP, LocalPlayerBounds.current)) {
        Box(
            modifier = modifier
                .alpha(opacity)
                .background(BACKGROUND, RoundedCornerShape(RADIUS))
                .padding(horizontal = PADDING_HORIZONTAL, vertical = PADDING_VERTICAL),
        ) {
            BasicText(
                text = text,
                style = TextStyle(color = Color.White, fontSize = TEXT_SIZE, lineHeight = TEXT_LINE_HEIGHT),
                // `white-space: nowrap`. A label that wraps changes the box's
                // width, and the clamp that keeps it on screen is computed from
                // that width.
                //
                // Not wrapping is not the same as being cut. `nowrap` keeps the
                // text whole and lets it overflow; Clip throws away whatever
                // does not fit, which is how "Kwaliteit: 1080p" was read off a
                // screen as "Kwaliteit: 108p" and taken for a ladder reporting a
                // 108-line rung. A tooltip that silently shortens a number is
                // worse than one that overflows, because the short number looks
                // like an answer.
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
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

/**
 * Above the control, centred on it, kept inside the window.
 *
 * `bottom: calc(100% + 8px)` and `clampPopOffset` in one place, and the first
 * caller of ChromeTooltip.leftFor with bounds that mean anything: the anchor's
 * position and the window's width are both given here, where a modifier inside the
 * button only ever knew about the button.
 */
internal class AboveAnchorPosition(
    private val gap: Dp,
    /**
     * The player, when the chrome has measured it.
     *
     * A Popup is its own window, so without this the clamp is the WINDOW's edges
     * and a label on a control near the player's left edge slides out across
     * whatever sits beside the player. The browser cannot do that: the popup is
     * a child of the player element. [Rect.Zero] falls back to the window, which
     * is what a chrome mounted without bounds had before.
     */
    private val player: Rect = Rect.Zero,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val measured: Boolean = player.width > 0f
        val left: Float = ChromeTooltip.leftFor(
            buttonCenter = anchorBounds.center.x.toFloat(),
            tooltipWidth = popupContentSize.width.toFloat(),
            boundsLeft = if (measured) player.left else 0f,
            boundsRight = if (measured) player.right else windowSize.width.toFloat(),
        )

        // Above, and clamped to the top edge: a control near the top of a small
        // window would otherwise place its label off-screen.
        val top: Int = anchorBounds.top - popupContentSize.height - gapPx
        return IntOffset(left.toInt(), top.coerceAtLeast(0))
    }

    // The gap in pixels. A position provider has no Density, so this is resolved
    // from the value it was constructed with rather than converted here.
    private val gapPx: Int = gap.value.toInt()
}
