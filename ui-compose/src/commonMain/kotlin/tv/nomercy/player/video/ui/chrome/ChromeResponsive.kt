// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

// Which controls survive a narrow window, and in what order they go.
//
// Ported from desktop-ui/helpers/responsive.ts rather than decided here. It is
// the one place a phone legitimately shows less than a desktop, and the whole
// point is that it shows less in the SAME order and at the SAME widths the web
// does: a viewer who knows the settings button disappears below 480 on the web
// should not find it disappearing at a different width, or a different button
// going first.
//
// Three rules, and they compose. A control the item cannot offer is not on the
// bar; portrait drops a fixed set regardless of width, because a tall thin
// window has room across but no room for a thumb to be precise; and everything
// else is decided by whether it still FITS, walking the priority list widest
// need first and adding footprints as it goes.
//
// That last one is not what this file did first. It cut the priority list at
// the breakpoint's hideAfterRank, which is what those two constants look like
// they mean sitting next to each other, and is not what the web does with
// either. See isControlVisible.

/** A control, by the name the web's priority list uses. */
public enum class ChromeControl {
    PLAY,
    MUTE,
    VOLUME,
    FULLSCREEN,
    SETTINGS,
    NEXT,
    PREVIOUS,
    CHAPTER_PREV,
    CHAPTER_NEXT,
    SEEK_BACK,
    SEEK_FORWARD,
    THEATER,
    PIP,
    SPEED,
    QUALITY,
    SUBTITLES,
    AUDIO,
    ASPECT_RATIO,
    PLAYLIST,
}

/**
 * Most essential first, least essential last. This is DEFAULT_PRIORITY from
 * responsive.ts, in order.
 *
 * The order is the decision: the bar is filled from the top of this list until
 * it runs out of room, so play survives a 320-pixel window and the playlist is
 * the first thing to go.
 */
public val CHROME_PRIORITY: List<ChromeControl> = listOf(
    ChromeControl.PLAY,
    ChromeControl.MUTE,
    ChromeControl.VOLUME,
    ChromeControl.FULLSCREEN,
    ChromeControl.SETTINGS,
    ChromeControl.NEXT,
    ChromeControl.PREVIOUS,
    ChromeControl.CHAPTER_PREV,
    ChromeControl.CHAPTER_NEXT,
    ChromeControl.SEEK_BACK,
    ChromeControl.SEEK_FORWARD,
    ChromeControl.THEATER,
    ChromeControl.PIP,
    ChromeControl.SPEED,
    ChromeControl.QUALITY,
    ChromeControl.SUBTITLES,
    ChromeControl.AUDIO,
    ChromeControl.ASPECT_RATIO,
    ChromeControl.PLAYLIST,
)

/**
 * Hidden in portrait whatever the width. PORTRAIT_HIDDEN from responsive.ts.
 *
 * Not a width decision, which is why it is a separate set: a tall thin window
 * has room across the bar and no room for a thumb to hit one of nineteen
 * targets in it.
 */
public val CHROME_PORTRAIT_HIDDEN: Set<ChromeControl> = setOf(
    ChromeControl.CHAPTER_PREV,
    ChromeControl.CHAPTER_NEXT,
    ChromeControl.PREVIOUS,
    ChromeControl.NEXT,
    ChromeControl.SUBTITLES,
    ChromeControl.AUDIO,
    ChromeControl.QUALITY,
    ChromeControl.PLAYLIST,
)

/**
 * A width band, for reporting only.
 *
 * The web writes the band's name onto the container as `data-breakpoint` and
 * emits it as `layout:breakpoint` for older subscribers. That is ALL a band
 * does there.
 *
 * [hideAfterRank] is carried because the web's constant carries it, and it is
 * deliberately not consulted by [isControlVisible]. Reading `hideAfterRank: 8`
 * and concluding it decides what is drawn is the mistake this port made: the
 * ranks and the bands both exist, they look like they meet, and they never do.
 */
public data class ChromeBreakpoint(
    val name: String,
    val maxWidth: Int?,
    val hideAfterRank: Int,
)

/** DEFAULT_BREAKPOINTS from responsive.ts. Names and widths, for reporting. */
public val CHROME_BREAKPOINTS: List<ChromeBreakpoint> = listOf(
    ChromeBreakpoint("xs", maxWidth = 320, hideAfterRank = 1),
    ChromeBreakpoint("sm", maxWidth = 480, hideAfterRank = 4),
    ChromeBreakpoint("md", maxWidth = 720, hideAfterRank = 8),
    ChromeBreakpoint("lg", maxWidth = 1024, hideAfterRank = 13),
    ChromeBreakpoint("xl", maxWidth = null, hideAfterRank = CHROME_PRIORITY.lastIndex),
)

/** The band a width falls in. The last band has no ceiling and always matches. */
public fun breakpointFor(widthDp: Int): ChromeBreakpoint =
    CHROME_BREAKPOINTS.first { it.maxWidth == null || widthDp <= it.maxWidth }

/**
 * One control's width, from `buttonFootprint`.
 *
 * Mute is wider than the rest wherever there is a pointer, because the volume
 * slider expands beside it and the bar has to have room for the expansion
 * before it happens. Without a pointer the slider does not expand and mute
 * costs what everything else costs.
 */
public fun controlFootprint(control: ChromeControl, noHover: Boolean): Int =
    if (control == ChromeControl.MUTE && !noHover) {
        CHROME_BUTTON_WIDTH + CHROME_VOLUME_SLIDER_WIDTH
    } else {
        CHROME_BUTTON_WIDTH
    }

/**
 * Whether a control is drawn, by whether it still fits.
 *
 * This is `shouldShowButton` and the accumulation loop in
 * `applyAllVisibilityRules`, and it is NOT what this file did before. The
 * previous version cut the priority list at the band's `hideAfterRank`, which
 * reads as the obvious meaning of two constants that sit next to each other and
 * is not what the web does with either of them.
 *
 * The web walks the priority list most-essential first, adds each control's
 * footprint as it goes, and hides the first one that no longer fits in the
 * container minus [CHROME_RESERVED_WIDTH]. So visibility is continuous in
 * width, not stepped: a control appears the pixel it fits rather than at the
 * next band, and two widths inside one band show different numbers of controls.
 *
 * The rank cut and the accumulation agree often enough to look identical in a
 * screenshot at a round width, which is why this survived a review and a gate.
 * The browser export caught it: at 720 — the top of md, `hideAfterRank = 8` —
 * the web draws four controls, not nine.
 *
 * [contentHidden] is the caller's answer to rule 2: a control the item cannot
 * offer, such as subtitles on a file with no subtitle track. The web checks it
 * before the fit and does not charge it any width, so a hidden control does not
 * push a later one off the bar.
 */
public fun isControlVisible(
    control: ChromeControl,
    widthDp: Int,
    portrait: Boolean = false,
    noHover: Boolean = false,
    contentHidden: (ChromeControl) -> Boolean = { false },
): Boolean = control in visibleControls(widthDp, portrait, noHover, contentHidden)

/**
 * Everything drawn at this width, in priority order.
 *
 * Returned as a list rather than computed per control because the rule is
 * cumulative: whether the quality button fits depends on everything ranked
 * above it, so asking about one control in isolation means walking the whole
 * list anyway.
 */
public fun visibleControls(
    widthDp: Int,
    portrait: Boolean = false,
    noHover: Boolean = false,
    contentHidden: (ChromeControl) -> Boolean = { false },
): List<ChromeControl> {
    // The time labels, the divider and the bar's padding are not buttons and
    // are not in the priority list, but they take room on the same row.
    val available: Int = maxOf(0, widthDp - CHROME_RESERVED_WIDTH)

    var accumulated = 0
    val visible = mutableListOf<ChromeControl>()

    for (control in CHROME_PRIORITY) {
        // Both reasons are "this control is not on the bar at all", and
        // neither charges any width — which is the part that matters: a
        // control that is not drawn must not push a later one off the end.
        if (isAbsent(control, portrait, contentHidden)) continue

        val footprint: Int = controlFootprint(control, noHover)

        // Not a break. The web keeps walking, so a narrow control ranked below
        // a wide one that did not fit can still appear — and a loop that
        // stopped at the first miss would drop it.
        if (accumulated + footprint <= available) {
            accumulated += footprint
            visible += control
        }
    }

    return visible
}

// Rule 2 (the item cannot offer it) and rule 3 (portrait), which the web
// checks in different places and which mean the same thing to the bar.
private fun isAbsent(
    control: ChromeControl,
    portrait: Boolean,
    contentHidden: (ChromeControl) -> Boolean,
): Boolean {
    if (contentHidden(control)) return true
    return portrait && control in CHROME_PORTRAIT_HIDDEN
}

/** BUTTON_WIDTH from responsive.ts. */
public const val CHROME_BUTTON_WIDTH: Int = 40

/** VOL_SLIDER_EXPANDED_WIDTH from responsive.ts. */
public const val CHROME_VOLUME_SLIDER_WIDTH: Int = 96

/**
 * RESERVED_CHROME_WIDTH from responsive.ts: the elapsed time, the divider, the
 * remaining time and the row's padding. Not buttons, same row.
 */
public const val CHROME_RESERVED_WIDTH: Int = 148
