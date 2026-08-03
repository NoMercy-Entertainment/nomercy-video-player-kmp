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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    /**
     * `.menu-button-subtext` — a dimmer, smaller note at the row's trailing
     * edge, which is where Auto names the rung the engine settled on.
     *
     * Its own span in the web rather than more characters in the label: it is
     * 10px at 60% white against the label's 13 at full, so folding it into the
     * label string draws it in the wrong size and the wrong colour.
     */
    subLabel: String? = null,
    onSelect: () -> Unit,
) {
    var focused: Boolean by remember { mutableStateOf(false) }
    val interaction: MutableInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // `height: 32px` with `border-radius: 4px`. The row was 16dp of
            // padding on every side around a 22dp glyph — about 54dp tall, so a
            // list of five options filled a phone and the card scrolled where the
            // browser shows the lot.
            .height(ROW_HEIGHT)
            .clip(RoundedCornerShape(ROW_RADIUS))
            .background(rowFill(isCurrent))
            .focusOutline(focused)
            .then(tag?.let { Modifier.testTag(it) } ?: Modifier)
            // On the arrow walk wherever a menu is up — the web's nav finds every
            // `button` in the open pane, and a settings row is one of them.
            .menuNavEntry(LocalMenuNav.current)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
            .padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V)
            // Both, because a reader announces which is chosen and a test finds
            // the row by what it says.
            .semantics {
                contentDescription = label
                selected = isCurrent
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
    ) {
        // One colour, because the row is never filled light. It flipped to black
        // on focus to stay legible against the white fill that is now an edge.
        val tint: Color = Color.White

        icon?.let { RowGlyph(it, tint) }

        BasicText(
            text = label,
            style = TextStyle(color = tint, fontSize = LABEL_SIZE, fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .weight(1f)
                .then(tag?.let { Modifier.testTag("$it$LABEL_TAG_SUFFIX") } ?: Modifier),
        )

        RowTail(subLabel, isCurrent, tint)

        if (opensSubMenu) {
            RowGlyph(FluentIcons.ChevronR, tint)
        }
    }
}

// What sits after the label: the dimmer note, then the mark.
//
// `margin-left: auto` on both — the mark is the row's LAST element, not its
// first. Drawn in front of the label it pushed the chosen row's text a glyph
// right of every other row in the list, and no column reserved here puts that
// back without inventing an indent the web has not got.
//
// A mark as well as a colour, because colour alone is the distinction a viewer
// with no colour vision cannot make.
@Composable
private fun RowTail(subLabel: String?, current: Boolean, tint: Color) {
    subLabel?.takeIf { it.isNotBlank() }?.let { note ->
        BasicText(text = note, style = SUB_TEXT.copy(color = subTint()))
    }

    if (current) {
        RowGlyph(FluentIcons.Checkmark, tint, CHECK_SIZE)
    }
}

// `.language-button.is-active { background: rgba(255,255,255,0.2) }`, inside the
// focus fill which is stronger. The chosen row was marked by a glyph alone, so
// it read as a row with an extra character rather than as the row you are on.
// Only the chosen row is filled. Focus is drawn as an edge, not as a fill —
// see [focusOutline].
private fun rowFill(current: Boolean): Color =
    if (current) Color.White.copy(alpha = 0.2f) else Color.Transparent

/**
 * `.language-button:focus-visible { outline: 2px solid #fff; outline-offset: -2px }`.
 *
 * An edge inside the row's own bounds, which is what `outline-offset: -2px`
 * means and what Compose's border already does. This filled the whole row solid
 * white instead and flipped every glyph and both labels to black, so the row
 * under the arrow key looked like the CHOSEN row — and the actually-chosen row,
 * a 20% white wash, looked like the one merely focused. Two states drawn as each
 * other is worse than one of them missing.
 *
 * The filled treatment is real, but it belongs to a remote across a room:
 * [PlayerFocusStyle.Filled] is where it lives, and these rows are the pointer and
 * touch chrome's.
 */
private fun Modifier.focusOutline(focused: Boolean): Modifier =
    if (focused) border(FOCUS_RING_WIDTH, Color.White) else this

// 60% white, against a row that is never filled light enough to need anything
// else.
private fun subTint(): Color = Color.White.copy(alpha = 0.6f)

private val FOCUS_RING_WIDTH: Dp = 2.dp

// Never described to a reader. Both of a row's glyphs repeat what its label
// already says, and a screen reader announcing "Audio, audio, chevron right" is
// reading the decoration out loud.
@Composable
private fun RowGlyph(icon: ImageVector, tint: Color, size: Dp = ICON_SIZE) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(size),
    )
}

// `.menu-button-check { width: 20px; height: 20px }`.
private val CHECK_SIZE = 20.dp

// `font-size: 10px; font-weight: 600; color: rgba(255, 255, 255, 0.6)`.
private val SUB_TEXT = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

// So a test can measure where a row's TEXT starts. Measuring the row says
// nothing: every row in a list has the same left edge whatever is drawn inside
// it, which is how a ragged label column passed unnoticed.
internal const val LABEL_TAG_SUFFIX = "-label"
// Read off the running player, where `.menu-button-text` is 13px at weight 600
// and the row's own padding is 16px on the leading edge.
//
// The label was 18sp — nearly 40% larger — and with 14dp of padding all round the
// rows were tall enough that six of them filled a 600px player. On screen beside
// the browser it did not read as a menu with big text; it read as a different
// component.
// `.language-button { height: 32px; padding: 4px 8px; border-radius: 4px }`.
private val ROW_HEIGHT = 32.dp
private val ROW_RADIUS = 4.dp
private val ROW_PADDING_H = 8.dp
private val ROW_PADDING_V = 4.dp
private val LABEL_SIZE = 13.sp

// The web renders its menu glyphs through the same svgFromIcon default the bar
// uses, and spaces them with the gap either side of `menu-button-text`.
private val ICON_SIZE = 20.dp
private val ICON_GAP = 8.dp
