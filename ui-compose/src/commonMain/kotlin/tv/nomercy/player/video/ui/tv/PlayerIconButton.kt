// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
) {
    var focused: Boolean by remember { mutableStateOf(false) }
    val interaction: MutableInteractionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(buttonSize)
            .background(if (focused) Color.White else Color.Transparent, CircleShape)
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
        // Foundation rather than Material. A player library that pulled Material
        // in would put it in every consumer's build whether or not they use it.
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                when {
                    !enabled -> DISABLED_TINT
                    focused -> Color.Black
                    else -> Color.White
                },
            ),
            modifier = Modifier.size(iconSize),
        )
    }
}

// Dim enough to read as unavailable, bright enough to still read as a control.
// A disabled button that disappears into the background is a hidden button with
// extra steps.
private val DISABLED_TINT = Color.White.copy(alpha = 0.35f)

// Big enough to read from a sofa. Television guidance puts the floor around this
// and a control below it is one people lean forward to identify.
// The web's, measured on the running player.
internal val WEB_BUTTON_SIZE: Dp = 40.dp
internal val WEB_ICON_SIZE: Dp = 22.dp

// A television's, which is what this file used for everything.
public val TV_BUTTON_SIZE: Dp = 48.dp
public val TV_ICON_SIZE: Dp = 28.dp
