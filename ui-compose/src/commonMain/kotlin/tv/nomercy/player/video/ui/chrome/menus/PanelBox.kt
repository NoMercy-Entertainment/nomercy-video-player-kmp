// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.tv.sidebarSeasons

/**
 * How wide and how tall the card may be, given the player it is drawn in.
 *
 * The port had `PANEL_WIDTH = 256.dp` — a constant, on a 4K television and in a
 * 400px sidebar alike. 256 is the web's `min-width: 16rem`, which is the FLOOR of
 * a `width: min-content` card, and reading a floor as the whole rule is why the
 * playlist pane had 256px to fit a 16rem seasons rail beside a 36rem episode rail
 * in. Both were drawn; the second one had no width left.
 *
 *     .menu-frame { width: min-content;
 *                   max-width: min(52rem, calc(100% - 2rem));
 *                   max-height: calc(100% - 2rem) }
 *     .main-menu  { min-width: 16rem; max-height: 60vh }
 *
 * So: what the pane needs, clamped by what the player has, floored at 16rem —
 * and the floor wins over the clamp, which is CSS's own precedence between
 * `min-width` and `max-width`.
 *
 * A null ceiling is no ceiling, which is the honest answer for a chrome measured
 * with an unbounded height: `room * 0.6` on an infinity is an infinity, and a
 * scrolling pane handed one does not degrade, it throws.
 *
 * The portrait playlist is the one pane with a rule of its own:
 *
 *     &[data-orientation='portrait'] .menu-frame:has(.playlist-menu.is-open) {
 *         top: 0; right: 0; left: 0; bottom: 0;
 *         max-width: 100%; width: 100%;
 *         max-height: 100%; height: 100%;
 *         border-radius: 0;
 *     }
 *
 * Full viewport, no insets, no rounding — and ONLY for the playlist. The
 * stylesheet says so in as many words: "Subtitle / audio / quality / speed /
 * aspect sub-menus keep their default popover sizing — they were fine."
 */
internal fun panelBoxOf(
    menu: MenuState,
    queue: List<TvChromeItem>,
    roomWidth: Dp,
    roomHeight: Dp,
    portrait: Boolean = false,
): PanelBox {
    if (portrait && menu == MenuState.Playlist) {
        return PanelBox(
            width = roomWidth,
            maxHeight = roomHeight.takeIf { it.value.isFinite() },
            fullBleed = true,
        )
    }

    val widest: Dp = (roomWidth - FRAME_INSET * 2).coerceAtMost(FRAME_MAX_WIDTH)

    return PanelBox(
        width = contentWidthOf(menu, queue).coerceAtMost(widest).coerceAtLeast(PANEL_MIN_WIDTH),
        // What `top: 16px` and `bottom: 52px` leave between them.
        //
        // `.menu-frame` also carries `max-height: calc(100% - 2rem)`, but that
        // cap never binds: the frame is positioned from both edges, so its
        // height is already the smaller `100% - 16px - 52px`. Subtracting the
        // 2rem instead let the card run 36dp past the bottom inset it is
        // supposed to sit on, and its last rows fell off the player.
        maxHeight = if (roomHeight.value.isFinite()) {
            (roomHeight - FRAME_INSET - FRAME_BOTTOM_INSET).coerceAtLeast(0.dp)
        } else {
            null
        },
    )
}

internal data class PanelBox(
    val width: Dp,
    val maxHeight: Dp?,
    /** The portrait playlist's full-viewport card: inset 0, radius 0, height 100%. */
    val fullBleed: Boolean = false,
) {
    /** `border-radius: 0` on the full-viewport card, 8px on the popover. */
    val radius: Dp get() = if (fullBleed) 0.dp else PANEL_RADIUS
}

/**
 * `(orientation: portrait)` — true when the height meets or exceeds the width,
 * which is the media query's own definition. Never true for an unbounded
 * measurement: an infinity is not a tall window, it is a harness that has not
 * decided, and the popover card is the shape that copes with that.
 */
internal fun isPortrait(width: Dp, height: Dp): Boolean =
    width.value.isFinite() && height.value.isFinite() && height >= width

/**
 * `width: min-content` — what the open pane cannot be drawn narrower than.
 *
 * Every pane but the playlist is a single column of rows with nothing that
 * demands width, so `.main-menu`'s own `min-width: 16rem` is its content width.
 * The playlist is two flex children with floors of their own — 16rem of seasons
 * and 36rem of episodes — and their sum is 52rem, which is exactly the frame's
 * `max-width` and not a coincidence.
 *
 * The rail is only there when `sidebarSeasons` says so, so the flat case asks for
 * the episode rail's floor alone rather than for room it will not use.
 */
private fun contentWidthOf(menu: MenuState, queue: List<TvChromeItem>): Dp = when {
    menu != MenuState.Playlist -> PANEL_MIN_WIDTH
    sidebarSeasons(queue).isNotEmpty() -> SEASONS_MIN_WIDTH + EPISODES_MIN_WIDTH
    else -> EPISODES_MIN_WIDTH
}

/**
 * The frame's insets — 16px on three sides, the bar's 52px below — or none at
 * all: the portrait playlist's `top: 0; right: 0; left: 0; bottom: 0` overlays
 * the bottom bar too, which its own comment calls "full real estate".
 */
internal fun panelInsets(panel: PanelBox): PaddingValues =
    if (panel.fullBleed) {
        PaddingValues(0.dp)
    } else {
        PaddingValues(
            start = FRAME_INSET,
            top = FRAME_INSET,
            end = FRAME_INSET,
            bottom = FRAME_BOTTOM_INSET,
        )
    }

/**
 * `height: auto; max-height: 60vh` for the popover — a ceiling, not a height —
 * and `height: 100%` for the portrait playlist, which is a height. Unbounded
 * gets neither, for the reason panelBoxOf's ceiling is nullable.
 */
internal fun panelHeight(panel: PanelBox): Modifier = when {
    panel.maxHeight == null -> Modifier
    panel.fullBleed -> Modifier.height(panel.maxHeight)
    else -> Modifier.heightIn(max = panel.maxHeight)
}

// Read off .menu-frame, .main-menu and .menu-header on the running player.
//
// The card used to be a full-width black sheet: SCRIM was black at 0.9 and the
// only geometry was 16dp of padding, so the settings list covered the picture and
// its rows landed on the transport.
internal val FRAME_INSET = 16.dp

// The bar's own height. `bottom: 52px` is what lifts the card clear of it.
internal val FRAME_BOTTOM_INSET = 52.dp

// `.main-menu { min-width: 16rem }` — the FLOOR of the card, which the port used
// as its whole width. See panelBoxOf for what the rest of the rule is.
internal val PANEL_MIN_WIDTH = 256.dp

// `.menu-frame { max-width: min(52rem, calc(100% - 2rem)) }`. 52rem is not an
// arbitrary ceiling: it is exactly 16rem of seasons plus 36rem of episodes, so
// the widest pane the player has fits it and nothing is allowed past it.
internal val FRAME_MAX_WIDTH = 832.dp

internal val PANEL_RADIUS = 8.dp
