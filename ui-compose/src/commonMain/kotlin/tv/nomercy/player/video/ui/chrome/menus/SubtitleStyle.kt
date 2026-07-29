// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import tv.nomercy.player.core.events.SubtitleStyle

// The subtitle settings list: which rows, what each reads, what each writes.
//
// The style itself is core's `SubtitleStyle`, not a second copy. There was one
// here for a while and that was the duplicate-concept failure this codebase
// keeps finding elsewhere: core already carried the type, the player already
// had an accessor for it, and the accessor already emitted the event a renderer
// listens to. A parallel type meant the menu wrote to something nothing was
// watching.

/**
 * What is drawn around a glyph so it stays readable over a bright frame.
 *
 * The web's six, with the web's tokens. `textShadow` is the default there and
 * here, because a subtitle with no edge treatment disappears into a snow scene.
 */
public enum class SubtitleEdgeStyle(public val token: String) {
    None("none"),
    Depressed("depressed"),
    DropShadow("dropShadow"),
    TextShadow("textShadow"),
    Raised("raised"),
    Uniform("uniform"),
    ;

    public companion object {
        public fun fromToken(token: String): SubtitleEdgeStyle =
            entries.firstOrNull { it.token == token }
                ?: throw IllegalArgumentException("unknown SubtitleEdgeStyle token: $token")
    }
}

/**
 * One row of the subtitle settings list.
 *
 * The web's `SETTING_ROWS`, in order, and the last one is the reset — which is
 * why this is a sealed shape rather than a list of properties. Reset is a row in
 * the same list that does not open a pane, and modelling it as a tenth property
 * would give it a value to show and a chevron it should not have.
 */
public enum class SubtitleSetting(public val property: String) {
    Font("fontFamily"),
    TextSize("fontSize"),
    TextColor("textColor"),
    TextOpacity("textOpacity"),
    EdgeStyle("edgeStyle"),
    BackgroundColor("backgroundColor"),
    BackgroundOpacity("backgroundOpacity"),
    AreaColor("areaColor"),
    AreaOpacity("windowOpacity"),
    ;

    /** What this row currently reads, which the web shows as `menu-button-subtext`. */
    public fun valueOf(style: SubtitleStyle): String = when (this) {
        Font -> style.fontFamily.substringBefore(",").trim()
        TextSize -> "${style.fontSize}%"
        TextColor -> style.textColor.replaceFirstChar { it.uppercase() }
        TextOpacity -> "${style.textOpacity}%"
        EdgeStyle -> style.edgeStyle
        BackgroundColor -> style.backgroundColor.replaceFirstChar { it.uppercase() }
        BackgroundOpacity -> "${style.backgroundOpacity}%"
        AreaColor -> style.areaColor.replaceFirstChar { it.uppercase() }
        AreaOpacity -> "${style.windowOpacity}%"
    }

    /**
     * What this row's own pane offers, verbatim from the web's lists.
     *
     * Five fonts, seven sizes, eight colours and five opacities — not a range
     * and not a slider. The web offers exactly these because they are the ones
     * that read on a television across a room, and a native player offering a
     * continuous slider would let somebody pick a size that has no counterpart
     * when they open the same account in a browser.
     */
    public fun choices(): List<String> = when (this) {
        Font -> FONT_FAMILIES
        TextSize -> PERCENTAGES.map { "$it%" }
        TextColor, BackgroundColor, AreaColor -> COLORS
        TextOpacity, BackgroundOpacity, AreaOpacity -> OPACITIES.map { "$it%" }
        EdgeStyle -> SubtitleEdgeStyle.entries.map { it.token }
    }

    /** The value this row would write, given a choice from its own pane. */
    public fun applied(style: SubtitleStyle, choice: String): SubtitleStyle = when (this) {
        Font -> style.copy(fontFamily = choice)
        TextSize -> style.copy(fontSize = percentOf(choice, style.fontSize))
        TextColor -> style.copy(textColor = choice)
        TextOpacity -> style.copy(textOpacity = percentOf(choice, style.textOpacity))
        // Parsed on the way in so an unknown token is refused here rather
        // than reaching a renderer that has to guess what it meant.
        EdgeStyle -> style.copy(edgeStyle = SubtitleEdgeStyle.fromToken(choice).token)
        BackgroundColor -> style.copy(backgroundColor = choice)
        BackgroundOpacity -> style.copy(backgroundOpacity = percentOf(choice, style.backgroundOpacity))
        AreaColor -> style.copy(areaColor = choice)
        AreaOpacity -> style.copy(windowOpacity = percentOf(choice, style.windowOpacity))
    }
}

// A choice arrives as the text of the row that was pressed, and "125%" is a
// number with a sign on it. Falling back to what was already set rather than to
// zero, because a value that failed to parse should leave the picture alone and
// not blank the subtitles.
private fun percentOf(choice: String, fallback: Int): Int =
    choice.trim().removeSuffix("%").trim().toIntOrNull() ?: fallback

// `fontFamilies` from desktop-ui/data/buttons.ts, stacks included. The fallback
// after the comma is the part that matters on a device without the first face,
// and dropping it here would give a viewer a different letterform than the
// browser draws for the same stored preference.
private val FONT_FAMILIES = listOf(
    "ReithSans, sans-serif",
    "Arial, sans-serif",
    "Courier New, monospace",
    "Georgia, sans-serif",
    "Verdana, sans-serif",
)

// `textSizes`, as percentages of the base.
private val PERCENTAGES = listOf(50, 75, 100, 150, 200, 300, 400)

// `colors`, in the web's order. Eight, and not a picker: a colour wheel would
// let somebody choose a subtitle the same shade as the frame behind it.
private val COLORS = listOf(BLACK, "blue", "cyan", "green", "magenta", "red", "yellow", "white")

// The one colour named three times: two defaults and the head of the list.
private const val BLACK = "black"

// `opacities`, in quarters.
private val OPACITIES = listOf(0, 25, 50, 75, 100)
