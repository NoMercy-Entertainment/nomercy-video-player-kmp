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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
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
    var pointerFocus: Boolean by remember { mutableStateOf(false) }
    val interaction: MutableInteractionSource = remember { MutableInteractionSource() }
    val focus: FocusManager = LocalFocusManager.current
    val focusVisible: Boolean = focusRingShows(focused, pointerFocus, focusStyle)
    val filled: Boolean = focusVisible && focusStyle == PlayerFocusStyle.Filled
    val hovered: Boolean by interaction.collectIsHoveredAsState()
    // A disabled control is not hovered, however the pointer is sitting on it:
    // `.btn:disabled:hover` inherits the disabled rule, not the hover one. Said
    // once, because three places asked and a fourth would eventually forget.
    val pointedAt: Boolean = hovered && enabled

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // requiredSize, not size. size() is still coerced by the parent's
            // constraints, so a 40dp button inside a shorter bar row measured
            // 40 wide and however tall the row allowed — and CircleShape over a
            // 40x32 box is an OVAL, which is what a viewer sees the moment they
            // hover or focus one.
            .requiredSize(buttonSize)
            .hoverPaint(pointedAt, hoverFill, shape)
            .focusPaint(focusVisible, focusStyle, shape)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { state ->
                focused = state.isFocused
                // Cleared when focus LEAVES, so one mouse click does not
                // suppress the ring for every keyboard focus that follows. The
                // flag describes how THIS focus arrived, not that the button
                // was ever clicked.
                if (!state.isFocused) pointerFocus = false
                onFocused(state.isFocused)
            }
            .markingPointerFocus { pointerFocus = true }
            // One activation path for a remote, a keyboard and a finger. It was
            // key events only, which is correct on a television and means a
            // pointer click does nothing — the same button is used by the touch
            // chrome, and that is where it was found.
            //
            // clickable already answers the centre of a pad and enter, so adding
            // a second handler for those would fire twice per press.
            // Focus is given back after a press, which is the whole of the web's
            // `:focus-visible` rule expressed here.
            //
            // `clickable` focuses the node it is on, and it also answers SPACE by
            // activating that node. So a pointer press on Pause left Pause holding
            // focus, and the next Space re-pressed the button instead of reaching
            // the player — playback control was lost to whatever was clicked last,
            // and the ring appeared on a control nobody had tabbed to. Clearing
            // returns focus to the chrome, which is where the key handler lives.
            // Tab never runs onClick, so keyboard navigation keeps its focus and
            // its ring exactly as before.
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = pressHandler(onClick, focusStyle, focus),
            )
            .announcedAs(description, enabled),
    ) {
        Glyph(
            glyphFor(icon, pointedAt || (active && enabled)),
            iconSize,
            glyphTint(enabled, filled),
            pointedAt,
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

// Whether the focus ring paints.
//
// A television focus is always visible: a remote has no pointer, so every focus
// there arrived from a key, and the ring is the only thing telling a viewer
// where they are. Suppressing it on a filled style would blind the D-pad.
private fun focusRingShows(
    focused: Boolean,
    fromPointer: Boolean,
    style: PlayerFocusStyle,
): Boolean = focused && (!fromPointer || style == PlayerFocusStyle.Filled)

// Announced as disabled, not merely drawn dim. A screen reader that read this as
// an ordinary button would send somebody to press it.
private fun Modifier.announcedAs(description: String, enabled: Boolean): Modifier =
    semantics {
        contentDescription = description
        if (!enabled) disabled()
    }

// Which INPUT gave this focus, because only a keyboard's shows.
//
// `:focus-visible`, which is the rule the web actually uses and the one this
// claimed to implement by clearing focus after a press. Clearing happens after
// the click, so a pointer press still took focus, painted the ring, and dropped
// it a frame later — a circle on every button anybody clicked. Marking the focus
// as not-visible BEFORE clickable can grant it means the ring never paints for a
// pointer; a key press leaves the flag alone and it does.
private fun Modifier.markingPointerFocus(onPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) onPress()
            }
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
// Activate, then give focus back — unless a television is driving.
//
// `clickable` focuses the node it is on AND answers SPACE by activating that
// node, so a pointer press on Pause left Pause holding focus and the next Space
// re-pressed the button instead of reaching the player. Playback control went to
// whatever was clicked last, and the ring appeared on a control nobody tabbed to.
//
// On a television the focus IS the cursor: a D-pad centre press must leave the
// highlight where it was or the next press has nowhere to go. Filled is the
// treatment a TV passes, so it is also the signal that focus is navigation.
private fun pressHandler(
    onClick: () -> Unit,
    focusStyle: PlayerFocusStyle,
    focus: FocusManager,
): () -> Unit = {
    onClick()
    if (focusStyle != PlayerFocusStyle.Filled) focus.clearFocus()
}

private fun Modifier.hoverPaint(hovered: Boolean, fill: Color, shape: Shape): Modifier =
    if (hovered) background(fill, shape) else this

private fun Modifier.focusPaint(focused: Boolean, style: PlayerFocusStyle, shape: Shape): Modifier = when {
    style == PlayerFocusStyle.None -> this
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

    /**
     * No visible treatment at all — the web's `:focus-visible`.
     *
     * A browser paints nothing when focus arrives programmatically, and a menu
     * opening focuses its first control so a keyboard can carry on from there.
     * Drawing the ring for that put a rounded-rectangle outline around the back
     * arrow of every submenu the moment it opened, which reads as a border
     * nobody asked for on one of two otherwise identical buttons.
     */
    None,
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
// Twenty-two, which is what `svgFromIcon(icon, size = 22)` writes as an SVG width attribute on
// every transport control. `.btn-icon` declares no width in CSS at all, so the stylesheet cannot
// tell you this number — deriving it from `.btn`'s 40px box minus its border and padding gives
// twenty, which is what this used to say and what the browser has never drawn.
internal val WEB_ICON_SIZE: Dp = 22.dp

// A television's, which is what this file used for everything.
public val TV_BUTTON_SIZE: Dp = 48.dp
public val TV_ICON_SIZE: Dp = 28.dp
