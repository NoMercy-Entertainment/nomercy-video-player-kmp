// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `buildKeyboardDecoration` — the keyboard in the corner of the shortcuts card,
 * with the letters of NOMERCY lit.
 *
 * It is the one piece of the overlay nobody would miss by accident and everybody
 * notices once, so it was worth porting exactly rather than approximating: the
 * same four rows, the same 28-unit pitch, the same staggered row offsets, the
 * same spacebar, all at four per cent and rotated eight degrees. The port had
 * none of it and the card read as a plain table.
 *
 * The letters themselves are not drawn. Compose cannot put text on a Canvas
 * without a text measurer, and at four per cent opacity behind a full card the
 * eleven-pixel glyphs are below the threshold of anything a screen shows — the
 * lit KEYS are what spells the word.
 */
@Composable
internal fun KeyboardDecoration(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(width = DECORATION_WIDTH, height = DECORATION_HEIGHT)
            .rotate(DECORATION_ANGLE)
            .alpha(DECORATION_ALPHA),
    ) {
        val unit: Float = size.width / VIEWBOX_WIDTH

        KEY_ROWS.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { keyIndex, letter ->
                drawKey(
                    at = Offset(
                        (keyIndex * KEY_PITCH + ROW_OFFSETS[rowIndex] + VIEWBOX_INSET) * unit,
                        (rowIndex * ROW_PITCH + VIEWBOX_INSET) * unit,
                    ),
                    unit = unit,
                    width = KEY_SIZE,
                    lit = letter in HIGHLIGHTED,
                )
            }
        }

        drawKey(
            at = Offset((SPACEBAR_X + VIEWBOX_INSET) * unit, (SPACEBAR_Y + VIEWBOX_INSET) * unit),
            unit = unit,
            width = SPACEBAR_WIDTH,
            lit = false,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKey(
    at: Offset,
    unit: Float,
    width: Float,
    lit: Boolean,
) {
    drawRoundRect(
        color = Color.White,
        topLeft = at,
        size = Size(width * unit, KEY_SIZE * unit),
        cornerRadius = CornerRadius(KEY_RADIUS * unit, KEY_RADIUS * unit),
        alpha = if (lit) 1f else UNLIT_ALPHA,
    )
}

// `'1234567890-='`, `'QWERTYUIOP'`, `'ASDFGHJKL'`, `'ZXCVBNM'`.
private val KEY_ROWS: List<List<Char>> = listOf(
    "1234567890-=".toList(),
    "QWERTYUIOP".toList(),
    "ASDFGHJKL".toList(),
    "ZXCVBNM".toList(),
)

private val HIGHLIGHTED: Set<Char> = "NOMERCY".toSet()

// `const rowOffsets = [0, 10, 22, 38]`.
private val ROW_OFFSETS: List<Int> = listOf(0, 10, 22, 38)

// `viewBox="-10 -10 412 164" width="450" height="180"`, rotate(-8deg), 0.04.
private const val VIEWBOX_WIDTH = 412f
private const val VIEWBOX_INSET = 10f
private val DECORATION_WIDTH: Dp = 450.dp
private val DECORATION_HEIGHT: Dp = 180.dp
private const val DECORATION_ANGLE = -8f
private const val DECORATION_ALPHA = 0.04f

// `x = keyIdx * 28`, `y = rowIdx * 30`, `width="24" height="24" rx="4"`.
private const val KEY_PITCH = 28f
private const val ROW_PITCH = 30f
private const val KEY_SIZE = 24f
private const val KEY_RADIUS = 4f
private const val UNLIT_ALPHA = 0.35f

// `<rect x="110" y="120" width="160" height="24" rx="4" opacity="0.35"/>`.
private const val SPACEBAR_X = 110f
private const val SPACEBAR_Y = 120f
private const val SPACEBAR_WIDTH = 160f
