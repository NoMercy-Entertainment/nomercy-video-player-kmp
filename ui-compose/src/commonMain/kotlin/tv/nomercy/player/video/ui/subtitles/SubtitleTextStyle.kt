// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tv.nomercy.player.core.events.SubtitleStyle

// The viewer's caption styling, as something Compose can draw with.
//
// Values are the web overlay's, not approximations of it: 2.5% of the picture's
// inline size clamped to 14..56, weight 500, 1.2 line height, and the same edge
// offsets the settings menu has been writing into saved preferences.
public fun SubtitleStyle.toTextStyle(
    // The picture's width in dp. The web sizes cues in `cqi` against the video
    // rect, so a fixed sp lands too small on a TV and too large on a phone.
    containerWidthDp: Float = BASELINE_CONTAINER_DP,
    // The host's own faces, by the name the stack asks for. Returning null falls
    // back to the generic family — a library cannot ship a consumer's font.
    fontResolver: (String) -> FontFamily? = { null },
): TextStyle {
    val size: Float = fontSizeSp(containerWidthDp)
    return TextStyle(
        color = parseColor(textColor, Color.White).withOpacity(textOpacity),
        fontSize = size.sp,
        lineHeight = (size * LINE_HEIGHT).sp,
        fontFamily = fontFamilyOf(fontFamily, fontResolver),
        fontWeight = FontWeight.Medium,
        shadow = edgeShadow(),
    )
}

/**
 * The black pass drawn under the text, or null when the edge style is a plain
 * offset shadow that [toTextStyle] already carries.
 *
 * `textShadow` and `uniform` are haloes — the web stacks seven blurred copies
 * for the first — and a single Compose [Shadow] cannot reach that density, so
 * a stroked outline of the same glyphs carries it instead.
 */
public fun SubtitleStyle.toOutlineStyle(base: TextStyle, widthPx: Float): TextStyle? =
    if (!isHaloEdge()) {
        null
    } else {
        base.copy(
            color = Color.Black.withOpacity(textOpacity),
            drawStyle = Stroke(width = widthPx.coerceAtLeast(MIN_OUTLINE_PX)),
            shadow = null,
        )
    }

/** How thick the outline pass is, as a fraction of the drawn font size. */
public const val SubtitleOutlineRatio: Float = 0.09f

/** The caption box behind the text, separate from the text's own colour. */
public fun SubtitleStyle.toBackgroundColor(): Color =
    parseColor(backgroundColor, Color.Black).withOpacity(backgroundOpacity)

/** The window behind the whole cue area — the web's `areaColor`/`windowOpacity`. */
public fun SubtitleStyle.toAreaColor(): Color =
    parseColor(areaColor, Color.Black).withOpacity(windowOpacity)

internal fun SubtitleStyle.fontSizeSp(containerWidthDp: Float): Float =
    (containerWidthDp * (CONTAINER_RATIO / PERCENT) * (fontSize / PERCENT)).coerceIn(MIN_FONT_SP, MAX_FONT_SP)

/**
 * Only `textShadow` gets the stroked pass, which is what makes it an outline.
 *
 * `uniform` used to be in here too, and the two then drew the same pixels: the
 * same zero-offset blur underneath and the same stroke on top, so two rows in
 * the menu were one style under two names (Stoney: "outline and uniform are the
 * same so one of them is useless").
 *
 * The web separates them by density, not by shape — `uniform` is one copy of
 * `0 0 4px`, `textShadow` is that same shadow drawn seven times. Seven stacked
 * blurs read as a hard edge, one reads as a glow. A single Compose [Shadow]
 * cannot be stacked, so the stroke stands in for the dense one and the plain
 * blur is left to carry the soft one on its own.
 */
private fun SubtitleStyle.isHaloEdge(): Boolean = edgeStyle == EDGE_TEXT_SHADOW

private fun SubtitleStyle.edgeShadow(): Shadow? {
    val black: Color = Color.Black.withOpacity(textOpacity)
    return when (edgeStyle) {
        EDGE_DEPRESSED -> Shadow(black, Offset(NEAR_OFFSET_PX, NEAR_OFFSET_PX), NEAR_BLUR_PX)
        EDGE_DROP_SHADOW -> Shadow(black, Offset(FAR_OFFSET_PX, FAR_OFFSET_PX), WIDE_BLUR_PX)
        EDGE_RAISED -> Shadow(black, Offset(-NEAR_OFFSET_PX, -NEAR_OFFSET_PX), NEAR_BLUR_PX)
        EDGE_UNIFORM, EDGE_TEXT_SHADOW -> Shadow(black, Offset.Zero, WIDE_BLUR_PX)
        else -> null
    }
}

private fun Color.withOpacity(percent: Int): Color = copy(alpha = (percent / PERCENT).coerceIn(0f, 1f))

/**
 * The first name in the stack any side can actually supply.
 *
 * A CSS font stack is a preference list, and only the first entry was ever read:
 * `"Courier New, monospace"` on a device without Courier New fell straight to
 * sans-serif, so a viewer's monospace pick looked identical to their Arial one.
 * The host is asked for every name before the generics are, because a host that
 * ships a face should win over a generic the stack names later.
 */
private fun fontFamilyOf(stack: String, resolver: (String) -> FontFamily?): FontFamily {
    val names: List<String> = stack.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    names.forEach { name -> resolver(name)?.let { return it } }
    names.forEach { name -> genericFamilyOf(name)?.let { return it } }
    return FontFamily.SansSerif
}

/** The CSS generic families, which every platform can draw without a font file. */
private fun genericFamilyOf(name: String): FontFamily? = when (name.lowercase()) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "cursive", "casual" -> FontFamily.Cursive
    "sans-serif" -> FontFamily.SansSerif
    else -> null
}

private fun parseColor(value: String, fallback: Color): Color {
    val hex: String = if (value.startsWith("#")) value else namedToHex(value) ?: return fallback
    val digits: String = hex.removePrefix("#")
    val rgb: Long = digits.toLongOrNull(HEX_RADIX) ?: return fallback
    return if (digits.length == RGB_DIGITS) Color(rgb or OPAQUE) else Color(rgb)
}

// The CSS names the web's own picker offers, and nothing beyond them — this is
// not a guess at the full CSS table.
//
// The two trios must offer the same colours, or a style set on one platform
// lands on the other as a colour its picker cannot show. `orange` was in the
// television's list and not the web's, which is exactly that divergence.
private fun namedToHex(name: String): String? = when (name.lowercase()) {
    "white" -> "#FFFFFF"
    "black" -> "#000000"
    "yellow" -> "#FFFF00"
    "red" -> "#FF0000"
    "green" -> "#00FF00"
    "blue" -> "#0000FF"
    "cyan" -> "#00FFFF"
    "magenta" -> "#FF00FF"
    "orange" -> "#FFA500"
    "purple" -> "#800080"
    "gray" -> "#808080"
    "grey" -> "#808080"
    else -> null
}

private const val EDGE_TEXT_SHADOW = "textShadow"
private const val EDGE_UNIFORM = "uniform"
private const val EDGE_DEPRESSED = "depressed"
private const val EDGE_DROP_SHADOW = "dropShadow"
private const val EDGE_RAISED = "raised"

private const val BASELINE_CONTAINER_DP = 960f
private const val CONTAINER_RATIO = 2.5f
private const val MIN_FONT_SP = 14f
private const val MAX_FONT_SP = 56f
private const val LINE_HEIGHT = 1.2f
private const val MIN_OUTLINE_PX = 1f

// The web's own edge offsets and blurs, in the two sizes its table uses:
// `depressed`/`raised` sit one pixel out under a 2px blur, `dropShadow` two out
// under 4px, and the haloes stay put under that same wider blur.
private const val NEAR_OFFSET_PX = 1f
private const val FAR_OFFSET_PX = 2f
private const val NEAR_BLUR_PX = 2f
private const val WIDE_BLUR_PX = 4f
private const val PERCENT = 100f
private const val HEX_RADIX = 16
private const val RGB_DIGITS = 6
private const val OPAQUE = 0xFF000000L
