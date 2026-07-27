// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A row in any of the settings lists.
//
// One widget for all of them, because a settings row is the same thing every
// time. Clickable rather than key-handled so a finger, a pointer and a remote
// all reach it: clickable already answers the centre of a pad and enter.
@Composable
internal fun MenuRow(
    label: String,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    tag: String? = null,
    onSelect: () -> Unit,
) {
    var focused: Boolean by remember { mutableStateOf(false) }
    val interaction: MutableInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (focused) Color.White else Color.Transparent)
            .then(tag?.let { Modifier.testTag(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
            .padding(ROW_PADDING)
            // Both, because a reader announces which is chosen and a test finds
            // the row by what it says.
            .semantics {
                contentDescription = label
                selected = isCurrent
            },
    ) {
        // A mark as well as a colour. Colour alone is the distinction a viewer
        // with no colour vision cannot make, and this is the row telling them
        // what they are already watching with.
        BasicText(
            text = if (isCurrent) "$CURRENT_MARK $label" else label,
            style = TextStyle(color = if (focused) Color.Black else Color.White, fontSize = LABEL_SIZE),
        )
    }
}

private const val CURRENT_MARK = "•"
private val ROW_PADDING = 14.dp
private val LABEL_SIZE = 18.sp
