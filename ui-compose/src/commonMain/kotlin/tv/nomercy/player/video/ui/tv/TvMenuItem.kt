// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A row in any of the lists a viewer opens from the chrome.
//
// One widget for all of them, because a television list is the same thing every
// time: a label, sometimes a second line, whether it is the current choice, and
// a visible focus. Four separate implementations is how three of them end up
// with a focus treatment nobody can see.
@Composable
public fun TvMenuItem(
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    isCurrent: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    var focused: Boolean by remember { mutableStateOf(false) }
    val labelColour: Color = if (focused) Color.Black else Color.White
    val detailColour: Color = if (focused) Color.DarkGray else Color.LightGray
    val rowColour: Color = if (focused) Color.White else Color.Transparent
    val text: String = if (isCurrent) "$CURRENT_MARK $label" else label

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(rowColour)
            .padding(ITEM_PADDING)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                val activated: Boolean = isActivation(event.key, event.type)
                if (activated) onSelect()
                activated
            }
            // Both, because a reader has to announce which one is already
            // chosen and a test has to find the row by what it says.
            .semantics {
                contentDescription = label
                selected = isCurrent
            },
    ) {
        BasicText(text = text, style = TextStyle(color = labelColour, fontSize = LABEL_SIZE))

        if (detail != null) {
            BasicText(text = detail, style = TextStyle(color = detailColour, fontSize = DETAIL_SIZE))
        }
    }
}

// A mark rather than a colour. Colour alone is the one distinction a viewer with
// no colour vision cannot make, and this is the row telling them what they are
// already listening to.
private const val CURRENT_MARK = "•"

private val ITEM_PADDING = 16.dp
private val LABEL_SIZE = 20.sp
private val DETAIL_SIZE = 14.sp
