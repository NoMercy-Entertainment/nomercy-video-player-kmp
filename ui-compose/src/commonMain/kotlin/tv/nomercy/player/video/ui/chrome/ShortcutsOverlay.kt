// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One shortcut: the keys, and what they do.
 *
 * `keys` is a list because a chord is more than one — the web renders them with a
 * `.keybinds-plus` between, and collapsing them to a single string would lose which
 * part is a modifier.
 */
public data class ShortcutEntry(
    val keys: List<String>,
    val label: String,
)

/** A titled column of shortcuts. The web has nine. */
public data class ShortcutGroup(
    val title: String,
    val entries: List<ShortcutEntry>,
)

/**
 * The whole keyboard reference, over the picture.
 *
 * `buildShortcutsOverlay`, which had nothing on this side at all. The key handler
 * bound the keys — that part was ported — and there was no way to find out they
 * existed. A player with thirty-four shortcuts and no list of them has thirty-four
 * secrets.
 *
 * Every number is one declaration in `.keybinds-*`: a 14px card at 24/28 padding on
 * an 85% black scrim, a three-column grid with 28/60 gaps, uppercase group titles at
 * half white, and keys in bordered monospace chips.
 */
@Composable
public fun ShortcutsOverlay(
    title: String,
    groups: List<ShortcutGroup>,
    hint: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        // `inset: 0` over everything at `z-index: 200`, and a press anywhere on the
        // scrim closes it — the web's overlay listens on itself, not only on a
        // button, because a viewer who opened this by accident should not have to
        // hunt for the way out.
        modifier = modifier
            .fillMaxSize()
            .background(SCRIM)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .testTag(SHORTCUTS_TAG),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(CARD_BACKGROUND, RoundedCornerShape(CARD_RADIUS))
                .padding(horizontal = CARD_PADDING_HORIZONTAL, vertical = CARD_PADDING_VERTICAL)
                // `overflow-y: auto` on the dialog. Nine groups do not fit a short
                // window, and a card that overflows silently hides whole groups.
                .verticalScroll(rememberScrollState()),
        ) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = HEADING_SIZE,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.padding(bottom = HEADING_GAP),
            )

            ShortcutColumns(groups)

            BasicText(
                text = hint,
                style = TextStyle(color = HINT_COLOR, fontSize = HINT_SIZE),
                modifier = Modifier.padding(top = HINT_GAP),
            )
        }
    }
}

// `grid-template-columns: repeat(3, 1fr)` with `gap: 28px 60px`.
//
// Compose has no grid in foundation, and a real one is not what this needs: the
// groups are dealt into three columns and each column stacks. Row-of-Columns says
// exactly that without pulling a layout library in.
@Composable
private fun ShortcutColumns(groups: List<ShortcutGroup>) {
    val columns: List<List<ShortcutGroup>> = List(GRID_COLUMNS) { column ->
        groups.filterIndexed { index, _ -> index % GRID_COLUMNS == column }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(GRID_COLUMN_GAP)) {
        for (column in columns) {
            Column(verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP)) {
                for (group in column) {
                    ShortcutGroupColumn(group)
                }
            }
        }
    }
}

@Composable
private fun ShortcutGroupColumn(group: ShortcutGroup) {
    Column(verticalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
        BasicText(
            text = group.title.uppercase(),
            style = TextStyle(
                color = GROUP_TITLE_COLOR,
                fontSize = GROUP_TITLE_SIZE,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = GROUP_TITLE_TRACKING,
            ),
            modifier = Modifier.padding(bottom = GROUP_TITLE_GAP),
        )

        for (entry in group.entries) {
            ShortcutRow(entry)
        }
    }
}

// `.keybinds-row` is `flex-direction: row-reverse` with `space-between`: the LABEL
// is on the left and the keys on the right, which reads as a reference rather than
// as a list of keys. Laying it out in source order puts the chips first and the row
// reads backwards.
@Composable
private fun ShortcutRow(entry: ShortcutEntry) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
        modifier = Modifier.padding(vertical = ROW_PADDING),
    ) {
        BasicText(
            text = entry.label,
            style = TextStyle(color = LABEL_COLOR, fontSize = LABEL_SIZE),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KEYS_GAP),
        ) {
            entry.keys.forEachIndexed { index, key ->
                if (index > 0) {
                    BasicText(
                        text = "+",
                        style = TextStyle(color = PLUS_COLOR, fontSize = PLUS_SIZE),
                    )
                }

                KeyChip(key)
            }
        }
    }
}

// `kbd.keybinds-key`: a bordered monospace chip.
@Composable
private fun KeyChip(key: String) {
    Box(
        modifier = Modifier
            .background(KEY_BACKGROUND, RoundedCornerShape(KEY_RADIUS))
            .border(KEY_BORDER_WIDTH, KEY_BORDER_COLOR, RoundedCornerShape(KEY_RADIUS))
            .padding(horizontal = KEY_PADDING_HORIZONTAL, vertical = KEY_PADDING_VERTICAL),
    ) {
        BasicText(
            text = key,
            style = TextStyle(
                color = Color.White,
                fontSize = KEY_SIZE,
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

// `.keybinds-dialog { background: rgba(0, 0, 0, 0.85) }`.
private val SCRIM: Color = Color.Black.copy(alpha = 0.85f)

// `.keybinds-card`: rgba(20, 20, 25, 0.8), radius 14, padding 24px 28px.
private val CARD_BACKGROUND: Color = Color(red = 20, green = 20, blue = 25, alpha = 204)
private val CARD_RADIUS: Dp = 14.dp
private val CARD_PADDING_VERTICAL: Dp = 24.dp
private val CARD_PADDING_HORIZONTAL: Dp = 28.dp

// `.keybinds-heading`: 19px, 600, and `margin: 0 0 14px 0`.
private val HEADING_SIZE = 19.sp
private val HEADING_GAP: Dp = 14.dp

// `.keybinds-grid`: three columns, `gap: 28px 60px` — row gap first, column second.
private const val GRID_COLUMNS = 3
private val GRID_ROW_GAP: Dp = 28.dp
private val GRID_COLUMN_GAP: Dp = 60.dp

// `.keybinds-column { gap: 14px }` and `.keybinds-group-title { margin-bottom: 3px }`.
private val COLUMN_GAP: Dp = 14.dp
private val GROUP_TITLE_GAP: Dp = 3.dp
private val GROUP_TITLE_SIZE = 14.sp
private val GROUP_TITLE_COLOR: Color = Color.White.copy(alpha = 0.5f)

// `letter-spacing: 0.05em` at 14px.
private val GROUP_TITLE_TRACKING = 0.7.sp

// `.keybinds-row { padding: 3px 0; gap: 14px }` and `.keybinds-keys { gap: 4px }`.
private val ROW_PADDING: Dp = 3.dp
private val ROW_GAP: Dp = 14.dp
private val KEYS_GAP: Dp = 4.dp

// `.keybinds-label`: 14px at 85% white. `.keybinds-plus`: 12px at 40%.
private val LABEL_SIZE = 14.sp
private val LABEL_COLOR: Color = Color.White.copy(alpha = 0.85f)
private val PLUS_SIZE = 12.sp
private val PLUS_COLOR: Color = Color.White.copy(alpha = 0.4f)

// `kbd.keybinds-key`: 12% white on a 20% border, radius 5, padding 2px 7px, 13px.
private val KEY_BACKGROUND: Color = Color.White.copy(alpha = 0.12f)
private val KEY_BORDER_COLOR: Color = Color.White.copy(alpha = 0.2f)
private val KEY_BORDER_WIDTH: Dp = 1.dp
private val KEY_RADIUS: Dp = 5.dp
private val KEY_PADDING_HORIZONTAL: Dp = 7.dp
private val KEY_PADDING_VERTICAL: Dp = 2.dp
private val KEY_SIZE = 13.sp

// `.keybinds-hint`: 13px at 35% white, `margin-top: 12px`.
private val HINT_SIZE = 13.sp
private val HINT_COLOR: Color = Color.White.copy(alpha = 0.35f)
private val HINT_GAP: Dp = 12.dp

internal const val SHORTCUTS_TAG = "nm-shortcuts-overlay"
