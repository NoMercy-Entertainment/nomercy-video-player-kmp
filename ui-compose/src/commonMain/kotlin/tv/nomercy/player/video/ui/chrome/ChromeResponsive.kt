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
// Two rules, and they compose. Rank decides what a narrow window drops, and
// portrait drops a fixed set regardless of width — a tall thin window has room
// across but no room for a viewer's thumb to be precise.

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
 * Most essential first, least essential last. A control's index here is its
 * rank, and a breakpoint hides everything ranked after its cut.
 *
 * This is DEFAULT_PRIORITY from responsive.ts, in order. The order is the
 * decision: play and mute survive a 320-pixel window, and the playlist is the
 * first thing to go.
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
 * A width band and the last rank it keeps.
 *
 * [maxWidth] null is the widest band, which keeps everything. The web writes
 * that one as `Infinity`.
 */
public data class ChromeBreakpoint(
    val name: String,
    val maxWidth: Int?,
    val hideAfterRank: Int,
)

/**
 * DEFAULT_BREAKPOINTS from responsive.ts, widths included.
 *
 * - xs (<= 320): play and mute only
 * - sm (<= 480): play, mute, volume, fullscreen, settings
 * - md (<= 720): plus the queue and chapter jumps
 * - lg (<= 1024): plus theater, pip and speed
 * - xl (> 1024): everything
 */
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
 * Whether a control is drawn at this width and orientation.
 *
 * A control absent from the priority list is always shown: the list decides
 * what collapses FIRST, not what exists, and a control nobody ranked should not
 * vanish because of that.
 */
public fun isControlVisible(
    control: ChromeControl,
    widthDp: Int,
    portrait: Boolean = false,
): Boolean {
    if (portrait && control in CHROME_PORTRAIT_HIDDEN) return false

    val rank: Int = CHROME_PRIORITY.indexOf(control)
    if (rank < 0) return true

    return rank <= breakpointFor(widthDp).hideAfterRank
}

/** Everything drawn at this width and orientation, in bar order. */
public fun visibleControls(widthDp: Int, portrait: Boolean = false): List<ChromeControl> =
    ChromeControl.entries.filter { isControlVisible(it, widthDp, portrait) }
