// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import tv.nomercy.player.video.ui.chrome.ControlTooltip
import tv.nomercy.player.video.ui.chrome.rememberTooltipVisible
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A round button on a television.
//
// Focus is drawn rather than hovered, because there is no pointer: the only way
// a viewer knows which button a press will hit is that it looks different. A
// control that does not visibly take focus is one nobody can use from a sofa.
//
// The description is required rather than optional. It is what a screen reader
// announces and what a test finds the button by, and an unlabelled icon button
// is invisible to both.
@Composable
public fun PlayerIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Drawn dimmed and unresponsive rather than removed.
     *
     * The web disables rather than hides — `setDisabled(prevBtn, onFirst)` — and
     * the difference is not cosmetic. A control that vanishes at the first item
     * and returns at the second reflows the whole bar, so every other control
     * moves under the viewer's finger exactly when they are pressing one.
     */
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
    /**
     * How big the target and the glyph are.
     *
     * The web's 40 and 22, measured off the running player rather than read off
     * the stylesheet — `.btn { width: 40px }`, `.btn-icon { width: 22px }`. They
     * were 48 and 28, which is a third too wide per control, and eighteen
     * controls a third too wide overflow the row: everything after the flex
     * divider was pushed past the right edge and simply not on screen. The
     * responsive filter was returning them the whole time.
     *
     * A television passes its own, because a 40dp target across a room is not
     * the same decision as one under a mouse.
     */
    buttonSize: Dp = WEB_BUTTON_SIZE,
    iconSize: Dp = WEB_ICON_SIZE,
    /**
     * What taking focus looks like, which is not one answer either.
     *
     * The web's is `outline: rgba(255,255,255,0.5) solid 2px` at
     * `outline-offset: -2px` — a ring inside the button's own edge, over a
     * `border: 2px solid transparent` that reserves the room for it. This drew a
     * FILLED white circle with the glyph flipped to black at every call site,
     * which is the television treatment: correct across a room, and a different
     * player to anyone tabbing through the desktop bar.
     *
     * A parameter rather than a form-factor flag, on the seam [buttonSize]
     * already established — a television is not a mouse, and it says so by
     * passing its own numbers.
     */
    focusStyle: PlayerFocusStyle = PlayerFocusStyle.Outline,
    /**
     * `.btn.is-active` — the control's own state is on, not merely pointed at.
     *
     * The web puts this on the same rule as `:hover`, so a muted volume or an
     * open menu reads exactly like a control under the pointer. There was no
     * active treatment here at all: a toggled control looked identical to an
     * untoggled one.
     */
    active: Boolean = false,
    /**
     * Round for a transport control, `border-radius: 6px` for a menu header's.
     *
     * `.btn` and `.menu-header-back` / `.menu-header-close` are two different
     * rules in the stylesheet and were one control here, so a pane's back arrow
     * and close cross were drawn as round transport buttons — the shape a viewer
     * reads as "this plays something".
     */
    shape: Shape = CircleShape,
    /**
     * `background: rgba(255, 255, 255, 0.08)` on a header button's hover, which
     * is the only hover fill in the chrome: `.btn:hover` is explicitly
     * `background: transparent`.
     */
    hoverFill: Color = Color.Transparent,
) {
    var focused: Boolean by remember { mutableStateOf(false) }
    val interaction: MutableInteractionSource = remember { MutableInteractionSource() }
    val filled: Boolean = focused && focusStyle == PlayerFocusStyle.Filled
    val hovered: Boolean by interaction.collectIsHoveredAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(buttonSize)
            .hoverPaint(hovered && enabled, hoverFill, shape)
            .focusPaint(focused, focusStyle, shape)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
            }
            // One activation path for a remote, a keyboard and a finger. It was
            // key events only, which is correct on a television and means a
            // pointer click does nothing — the same button is used by the touch
            // chrome, and that is where it was found.
            //
            // clickable already answers the centre of a pad and enter, so adding
            // a second handler for those would fire twice per press.
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            // Announced as disabled, not merely drawn dim. A screen reader that
            // read this as an ordinary button would send somebody to press it.
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            },
    ) {
        Glyph(
            glyphFor(icon, (hovered || active) && enabled),
            iconSize,
            glyphTint(enabled, filled),
            hovered && enabled,
        )

        // `wireTooltips` attaches one of these to all eighteen controls. The
        // arithmetic for placing it existed here with its own tests and no
        // renderer, so a pointer resting on a button said nothing.
        //
        // No flag guarding this for television: a tooltip appears on HOVER, and a
        // remote never produces one. Adding a form-factor parameter would be
        // describing that in a second place.
        ControlTooltip(text = description, visible = rememberTooltipVisible(interaction))
    }
}

// Which of the two drawings of a control is on screen.
//
// `.btn:hover .icon-normal { display: none }` with `.btn.is-active` on the same
// rule — the bar's whole visual language is outlined at rest and filled once a
// pointer is on it or the control is on. Every hover variant was generated and
// none was drawn, so the port answered a pointer with a 1.1 scale and nothing
// else, and an active control was indistinguishable from an idle one.
//
// Falls back to the normal drawing rather than failing: an icon a consumer
// supplied has no variant in the table, and a control with one drawing is a
// control that does not invert.
internal fun glyphFor(icon: ImageVector, inverted: Boolean): ImageVector =
    if (inverted) FluentIcons.hoverFor(icon) ?: icon else icon

// The glyph inside a control: tinted, and grown while a pointer is on it.
//
// `.btn:hover svg { transform: scale(1.1) }` over `transition: transform 0.18s`.
// Nothing here grew, so a pointer crossing the bar got no answer at all until it
// landed on something.
//
// Foundation rather than Material. A player library that pulled Material in
// would put it in every consumer's build whether or not they use it.
@Composable
private fun Glyph(
    icon: ImageVector,
    size: Dp,
    tint: Color,
    // Null on a surface with no pointer, which is a television: nothing there
    // can hover, so nothing there needs to grow.
    hovered: Boolean,
) {
    val scale: Float by animateFloatAsState(
        targetValue = if (hovered) HOVER_SCALE else 1f,
        animationSpec = tween(HOVER_MS),
        label = "glyph",
    )

    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(size).scale(scale),
    )
}

// The two focus treatments, drawn.
//
// A filled circle for a remote across a room, the web's translucent ring inside
// the button's own edge for a pointer — over the `border: 2px solid transparent`
// the base rule already reserves the room for.
// `background: rgba(255, 255, 255, 0.08)` while a pointer is on a header
// button, and nothing at all on a transport one - `.btn:hover` sets
// `background: transparent` explicitly.
private fun Modifier.hoverPaint(hovered: Boolean, fill: Color, shape: Shape): Modifier =
    if (hovered) background(fill, shape) else this

private fun Modifier.focusPaint(focused: Boolean, style: PlayerFocusStyle, shape: Shape): Modifier = when {
    focused && style == PlayerFocusStyle.Filled -> background(Color.White, shape)
    focused -> border(FOCUS_RING_WIDTH, FOCUS_RING_COLOR, shape)
    else -> this
}

// Black on the filled circle, dimmed when there is nothing to press, white
// otherwise. One decision rather than three conditions that happen to read the
// same state.
private fun glyphTint(enabled: Boolean, filled: Boolean): Color = when {
    !enabled -> DISABLED_TINT
    filled -> Color.Black
    else -> Color.White
}

/** Which of the two focus treatments a surface wants. */
public enum class PlayerFocusStyle {
    /** The web's ring, inside the button's own edge. */
    Outline,

    /** A filled circle with the glyph inverted, for a remote across a room. */
    Filled,
}

// `.btn[disabled] { opacity: 0.3 }`. This was 0.35, which is the same idea and
// not the same picture.
private val DISABLED_TINT = Color.White.copy(alpha = 0.3f)

// `outline: rgba(255, 255, 255, 0.5) solid 2px` at `outline-offset: -2px`, which
// is the width the base rule's transparent border already reserves.
private val FOCUS_RING_WIDTH: Dp = 2.dp
private val FOCUS_RING_COLOR: Color = Color.White.copy(alpha = 0.5f)

// `.btn:hover svg { transform: scale(1.1) }`, `transition: transform 0.18s`.
private const val HOVER_SCALE = 1.1f
private const val HOVER_MS = 180

// Big enough to read from a sofa. Television guidance puts the floor around this
// and a control below it is one people lean forward to identify.
// The web's, measured on the running player.
internal val WEB_BUTTON_SIZE: Dp = 40.dp
// `.btn` is 40px with `border: 2px` and `padding: 8px`, so the glyph inside it is
// twenty — not twenty-two. Two units over on a forty-unit control is the
// difference between an icon sitting in a button and an icon filling it.
internal val WEB_ICON_SIZE: Dp = 20.dp

// A television's, which is what this file used for everything.
public val TV_BUTTON_SIZE: Dp = 48.dp
public val TV_ICON_SIZE: Dp = 28.dp
