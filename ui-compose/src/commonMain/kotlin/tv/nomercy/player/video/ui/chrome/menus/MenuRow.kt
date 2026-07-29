// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import tv.nomercy.player.video.ui.tv.FluentIcons
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
    /**
     * The glyph the web puts in `menu-button-icon-left`.
     *
     * Only the main list's rows carry one. A choice inside a list — a language,
     * a bitrate — has no icon there either, and giving one to every row would
     * turn a list of options into a list of buttons.
     */
    icon: ImageVector? = null,
    /**
     * The `menu-button-chevron` on a row that opens another list.
     *
     * Absent on a row that picks something, which is the difference a viewer
     * reads before pressing: one of these goes somewhere and the other one ends.
     */
    opensSubMenu: Boolean = false,
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
    ) {
        val tint: Color = if (focused) Color.Black else Color.White

        icon?.let { RowGlyph(it, tint) }

        // A mark as well as a colour. Colour alone is the distinction a viewer
        // with no colour vision cannot make, and this is the row telling them
        // what they are already watching with.
        BasicText(
            text = if (isCurrent) "$CURRENT_MARK $label" else label,
            style = TextStyle(color = tint, fontSize = LABEL_SIZE),
            modifier = Modifier.weight(1f),
        )

        if (opensSubMenu) {
            RowGlyph(FluentIcons.ChevronR, tint)
        }
    }
}

// Never described to a reader. Both of a row's glyphs repeat what its label
// already says, and a screen reader announcing "Audio, audio, chevron right" is
// reading the decoration out loud.
@Composable
private fun RowGlyph(icon: ImageVector, tint: Color) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(ICON_SIZE),
    )
}

private const val CURRENT_MARK = "•"
private val ROW_PADDING = 14.dp
private val LABEL_SIZE = 18.sp

// The web renders its menu glyphs through the same svgFromIcon default the bar
// uses, and spaces them with the gap either side of `menu-button-text`.
private val ICON_SIZE = 22.dp
private val ICON_GAP = 12.dp
