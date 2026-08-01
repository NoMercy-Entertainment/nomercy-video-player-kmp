// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlin.math.roundToInt

// What a subtitle is painted with, as numbers rather than as tokens.
//
// The settings menu already writes `edgeStyle = "dropShadow"` and
// `textColor = "cyan"` into the style the player carries. Nothing native turned
// either of those words into anything: the tokens were the whole port, so every
// row in that menu wrote a preference that no renderer could act on.
//
// Ported from the web `subtitle-overlay` plugin's own two helpers, `getEdgeStyle`
// and `parseColorToHex`. Kept out of the Compose module deliberately — the
// numbers are not Compose's, a SwiftUI chrome needs the same ones, and a table
// of shadow offsets can be asserted without a window.

/**
 * The nine colours the web resolves without asking the browser.
 *
 * `NAMED_COLORS` verbatim. Eight of them are what the settings menu offers, and
 * `gray` is the ninth the web keeps for a style saved before that list settled.
 */
public val NAMED_SUBTITLE_COLORS: Map<String, String> = mapOf(
    "white" to "#FFFFFF",
    "black" to "#000000",
    "red" to "#FF0000",
    "green" to "#00FF00",
    "blue" to "#0000FF",
    "yellow" to "#FFFF00",
    "cyan" to "#00FFFF",
    "magenta" to "#FF00FF",
    "gray" to "#808080",
)

/**
 * A colour and an opacity, as `#RRGGBBAA`.
 *
 * The alpha is folded into the byte rather than left to the renderer, which is
 * the web's decision and the right one: a viewer who sets the background to 0%
 * expects it gone, and a colour handed over as `#000000` with the opacity
 * carried separately is one that some renderer down the line draws opaque.
 *
 * [opacity] is 0..1, not the menu's percentage. The caller divides, because the
 * three opacities in a style each apply to a different one of these calls.
 *
 * Named colours first, exactly as the web does it. What the web reaches for next
 * is a canvas, to normalise any CSS colour a browser understands; there is no
 * canvas here, so this reads the literal forms instead — `#RGB`, `#RRGGBB`,
 * `#RRGGBBAA`, `rgb(…)` and `rgba(…)` — and anything beyond them is transparent
 * rather than guessed. A wrong colour is worse than none: it is a subtitle the
 * viewer cannot read and cannot explain.
 */
public fun parseColorToHex(color: String, opacity: Double): String {
    val value: String = color.trim()
    val lower: String = value.lowercase()
    val named: String? = NAMED_SUBTITLE_COLORS[lower]

    return when {
        lower == "transparent" -> TRANSPARENT
        named != null -> normalizeHex(named, opacity)
        lower.startsWith("rgb") -> rgbToHex(value, opacity)
        value.startsWith("#") -> normalizeHex(value, opacity)
        else -> TRANSPARENT
    }
}

/**
 * `#RGB` widened, `#RRGGBB` given an alpha byte, `#RRGGBBAA` left alone.
 *
 * The last case is not an oversight and it is the web's behaviour: a colour that
 * already carries its own alpha has been given one deliberately, and folding the
 * style's opacity in on top would multiply two settings the viewer set once.
 */
public fun normalizeHex(hex: String, opacity: Double): String {
    val value: String = hex.uppercase()

    return when (value.length) {
        // Each digit twice, which is what widening `#RGB` means.
        SHORT_HEX_LENGTH -> {
            val widened: String = value.drop(1).flatMap { digit -> listOf(digit, digit) }.joinToString("")
            "#$widened${alphaByte(opacity)}"
        }

        HEX_LENGTH -> value + alphaByte(opacity)
        else -> value
    }
}

/** `rgb(0, 255, 0)` or `rgba(…)` — the first three numbers, then the opacity. */
public fun rgbToHex(rgb: String, opacity: Double): String {
    val channels: List<Int> = DIGITS.findAll(rgb)
        .map { it.value.toInt() }
        .take(CHANNELS)
        .toList()

    if (channels.size < CHANNELS) return TRANSPARENT

    return "#" + channels.joinToString("") { byteOf(it) } + alphaByte(opacity)
}

/**
 * What is drawn behind a glyph so it survives a bright frame.
 *
 * A shadow with a repeat count rather than a CSS string, because the web's
 * `textShadow` style is the same shadow written seven times — that is how a
 * browser turns a blur into an outline dense enough to read over snow, and a
 * port that kept one copy would draw a faint halo and look like a bug in the
 * font rather than a setting that did not survive.
 */
public data class SubtitleEdge(
    val offsetXPx: Double,
    val offsetYPx: Double,
    val blurPx: Double,
    /** How many times the same shadow is stacked. Zero is no edge at all. */
    val layers: Int,
)

/**
 * The web's `getEdgeStyle` table, token for token and pixel for pixel.
 *
 * Every number here is one a viewer already chose in a browser. These exact
 * offsets are what their saved preference means, so a `raised` that lost its
 * negative sign is not a near miss: it is the depressed style under another
 * name, and the setting they picked is unreachable.
 */
public fun subtitleEdgeOf(token: String): SubtitleEdge = when (token) {
    "depressed" -> SubtitleEdge(offsetXPx = 1.0, offsetYPx = 1.0, blurPx = 2.0, layers = 1)
    "dropShadow" -> SubtitleEdge(offsetXPx = 2.0, offsetYPx = 2.0, blurPx = 4.0, layers = 1)
    "raised" -> SubtitleEdge(offsetXPx = -1.0, offsetYPx = -1.0, blurPx = 2.0, layers = 1)
    "uniform" -> SubtitleEdge(offsetXPx = 0.0, offsetYPx = 0.0, blurPx = 4.0, layers = 1)
    "textShadow" -> SubtitleEdge(offsetXPx = 0.0, offsetYPx = 0.0, blurPx = 4.0, layers = TEXT_SHADOW_LAYERS)
    else -> NO_EDGE
}

/** No shadow — `none`, and anything the table does not name. */
public val NO_EDGE: SubtitleEdge = SubtitleEdge(offsetXPx = 0.0, offsetYPx = 0.0, blurPx = 0.0, layers = 0)

private fun alphaByte(opacity: Double): String = byteOf((opacity.coerceIn(0.0, 1.0) * FULL_BYTE).roundToInt())

private fun byteOf(value: Int): String = value.coerceIn(0, FULL_BYTE)
    .toString(HEX_RADIX)
    .uppercase()
    .padStart(2, '0')

private val DIGITS = Regex("\\d+")

private const val TRANSPARENT = "#00000000"

// `#RGB` and `#RRGGBB`, counting the hash.
private const val SHORT_HEX_LENGTH = 4
private const val HEX_LENGTH = 7

private const val CHANNELS = 3
private const val FULL_BYTE = 255
private const val HEX_RADIX = 16

// `Array.from({ length: 7 })` in the web's own textShadow case.
private const val TEXT_SHADOW_LAYERS = 7
