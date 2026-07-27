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
import androidx.compose.ui.semantics.semantics
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
    focusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
) {
    var focused: Boolean by remember { mutableStateOf(false) }
    val interaction: MutableInteractionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(BUTTON_SIZE)
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
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
    ) {
        // Foundation rather than Material. A player library that pulled Material
        // in would put it in every consumer's build whether or not they use it.
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (focused) Color.Black else Color.White),
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

// Big enough to read from a sofa. Television guidance puts the floor around this
// and a control below it is one people lean forward to identify.
private val BUTTON_SIZE = 48.dp
private val ICON_SIZE = 28.dp
