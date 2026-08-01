// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.nomercy.player.video.ui.chrome.ChromeState
import kotlin.math.roundToInt

/**
 * The portrait playlist, from the `@container` block styles.css keeps under
 * `&[data-orientation='portrait']` (lines 1060-1151).
 *
 * In portrait the two rails STACK:
 *
 *     .playlist-cols { flex-direction: column; overflow-y: auto; flex: 1 }
 *     .seasons-pane  { min-width: 0; border-right: none;
 *                      border-bottom: 2px solid rgba(107, 114, 128, 0.2);
 *                      height: auto; overflow: visible; width: 100% }
 *     .episode-menu  { min-width: 0; flex: 1; overflow-y: auto; width: 100% }
 *
 * The seasons list takes the height it needs and does not scroll on its own —
 * `height: auto; overflow: visible` — and the episode rail scrolls in whatever
 * is left, which is `weight(1f)` under a wrap-height column here. The landscape
 * hairline between the rails turns under the seasons list: same two pixels,
 * same grey, `border-bottom` instead of `border-right`.
 */
@Composable
internal fun PortraitPlaylist(picks: PlaylistPicks, seasons: List<Int>, state: ChromeState) {
    if (seasons.isEmpty()) {
        EpisodeRail(picks, picks.queue.indices.toList(), Modifier.fillMaxSize())
        return
    }

    // Seeded from what is playing, exactly as the landscape rail seeds itself.
    var chosen: Int by remember(seasons, state.queueIndex) {
        mutableStateOf(openingSeason(state, seasons))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PortraitSeasons(picks, seasons, chosen) { chosen = it }

        Box(Modifier.fillMaxWidth().height(RAIL_BORDER).background(RAIL_BORDER_COLOR))

        EpisodeRail(picks, episodeRows(picks.queue, chosen), Modifier.weight(1f).fillMaxWidth())
    }
}

// The seasons list, full width and only as tall as its rows — `height: auto;
// overflow: visible`, so it is a plain Column rather than a rail of its own.
// Same tag as the landscape rail: it is the same rail, laid the other way.
@Composable
private fun PortraitSeasons(
    picks: PlaylistPicks,
    seasons: List<Int>,
    chosen: Int,
    onChoose: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(RAIL_PADDING).testTag(SEASONS_RAIL_TAG)) {
        seasons.forEach { season ->
            MenuRow(seasonLabel(picks.strings, season), isCurrent = season == chosen, tag = "$ROW_SEASON$season") {
                onChoose(season)
            }
        }
    }
}

/** Which card rule applies: the base one, or the portrait override. */
internal fun playlistCards(portrait: Boolean): CardLayout =
    if (portrait) PORTRAIT_CARDS else LANDSCAPE_CARDS

/**
 * `width: 38%; flex-basis: 38%; max-width: 180px` — a share of the row with a
 * ceiling. `fillMaxWidth(share)` cannot say the ceiling half: a `widthIn` on
 * either side of it is overruled by the fixed constraints the fraction sets, so
 * the width is resolved here in one place.
 */
internal fun Modifier.rowShare(share: Float, cap: Dp?): Modifier = layout { measurable, constraints ->
    val room: Int = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
    val width: Int = (room * share).roundToInt()
        .let { wanted -> cap?.let { minOf(wanted, it.roundToPx()) } ?: wanted }

    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * Everything the portrait block rewrites about a card, and the base values it
 * rewrites them from. One value per orientation rather than flags at every use,
 * so the two rules can be read side by side the way the stylesheet holds them.
 */
internal data class CardLayout(
    /** `.episode-menu-button-left { width: … }` — the thumbnail's share of the row. */
    val thumbShare: Float,
    /** `max-width: 180px` in portrait; the base rule has no cap. */
    val thumbMaxWidth: Dp?,
    /** `align-self: center` in the base rule, `flex-start` in portrait. */
    val thumbAlignTop: Boolean,
    /** `.playlist-menu-button { gap: … }`. */
    val gap: Dp,
    /** `-webkit-line-clamp` on `.playlist-menu-button-overview`. */
    val overviewLines: Int,
    /** `.playlist-menu-button-title { font-size: … }`, at `line-height: 1.25`. */
    val titleSize: TextUnit,
    val titleLineHeight: TextUnit,
    /** Portrait's `margin-bottom: 4px` under the title; the base rule has none. */
    val titleBottomPad: Dp,
    /** The episode rail's `.scroll-container` padding and gap. */
    val rail: RailStyle,
)

internal data class RailStyle(val padding: PaddingValues, val gap: Dp)

// The base card, every number from the un-overridden rules PlaylistPane.kt
// quotes beside its own constants.
internal val LANDSCAPE_CARDS: CardLayout = CardLayout(
    thumbShare = 0.375f,
    thumbMaxWidth = null,
    thumbAlignTop = false,
    gap = 8.dp,
    overviewLines = 4,
    titleSize = 13.sp,
    titleLineHeight = 16.25.sp,
    titleBottomPad = 0.dp,
    rail = RailStyle(padding = RAIL_PADDING, gap = 0.dp),
)

// The portrait overrides, number for number:
//
//     .playlist-menu-button           { padding: 8px; gap: 10px; align-items: flex-start }
//     .episode-menu-button-left       { width: 38%; flex-basis: 38%; max-width: 180px;
//                                       align-self: flex-start; aspect-ratio: 16 / 9 }
//     .playlist-menu-button-overview  { -webkit-line-clamp: 3; line-clamp: 3 }
//     .playlist-menu-button-title     { font-size: 0.95rem; margin-bottom: 4px }
//     .episode-menu .scroll-container { gap: 6px; padding: 8px 6px }
//
// 0.95rem is 15.2 at the stylesheet's 16px rem, and its inherited line-height
// of 1.25 makes 19.
internal val PORTRAIT_CARDS: CardLayout = CardLayout(
    thumbShare = 0.38f,
    thumbMaxWidth = 180.dp,
    thumbAlignTop = true,
    gap = 10.dp,
    overviewLines = 3,
    titleSize = 15.2.sp,
    titleLineHeight = 19.sp,
    titleBottomPad = 4.dp,
    rail = RailStyle(padding = PaddingValues(horizontal = 6.dp, vertical = 8.dp), gap = 6.dp),
)
