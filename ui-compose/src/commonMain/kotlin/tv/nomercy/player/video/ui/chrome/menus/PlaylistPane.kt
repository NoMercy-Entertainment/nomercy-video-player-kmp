// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.tv.formatTime
import tv.nomercy.player.video.tv.sidebarSeasons
import tv.nomercy.player.video.ui.chrome.ChromeArtwork
import tv.nomercy.player.video.ui.chrome.ChromeCommands
import tv.nomercy.player.video.ui.chrome.ChromeState

/**
 * The playlist pane, ported from `renderPlaylistPane` + `buildPlaylistPaneShell`.
 *
 * Two layouts, and which one is `shouldShowSeasonSidebar`'s decision rather than
 * this composable's — the rule excludes movie collections and season-0 specials
 * as well as single seasons, and re-deriving it here is how the two come to
 * disagree. `sidebarSeasons` answers empty for every flat case, so a rail cannot
 * be drawn from a list the rule calls flat.
 *
 *     .playlist-cols  { display: flex; flex-direction: row; flex: 1 }
 *     .seasons-pane   { flex: 1; min-width: 16rem; border-right: 2px solid … }
 *     .episode-menu   { flex: 1; min-width: 36rem }
 *     .playlist-flat .seasons-pane { display: none }
 *
 * Both rails are `flex: 1` over a floor, which is `weight(1f)` over a `widthIn`
 * here. Below 16rem + 36rem of room the floors win and the right rail is clipped,
 * which is what the browser does as well — `.playlist-cols` carries
 * `overflow: hidden`.
 */
@Composable
internal fun PlaylistPane(
    state: ChromeState,
    commands: ChromeCommands,
    strings: MenuStrings,
    onMenuChange: (MenuState) -> Unit,
    // Unset draws no picture, which is the case for every host that has not
    // furnished the slot — see ChromeSlots.artwork.
    artwork: ChromeArtwork? = null,
    // The `data-orientation='portrait'` layout: the rails stack instead of
    // sitting side by side, and the cards change shape. See PlaylistPortrait.kt.
    portrait: Boolean = false,
) {
    val seasons: List<Int> = sidebarSeasons(state.queue)
    val picks = PlaylistPicks(state.queue, state.queueIndex, strings, artwork, playlistCards(portrait)) { item ->
        item.id?.let(commands::playQueueItem)
        onMenuChange(MenuState.Hidden)
    }

    when {
        portrait -> PortraitPlaylist(picks, seasons, state)
        seasons.isEmpty() -> EpisodeRail(picks, state.queue.indices.toList(), Modifier.fillMaxWidth())
        else -> SeasonedRails(picks, seasons, state)
    }
}

// The landscape two-column layout: seasons beside episodes, split by the
// hairline `.seasons-pane` carries as its right border.
@Composable
private fun SeasonedRails(picks: PlaylistPicks, seasons: List<Int>, state: ChromeState) {
    // Seeded from what is playing, which is what the web does: the pane opens on
    // the season somebody is already watching rather than on the first one.
    var chosen: Int by remember(seasons, state.queueIndex) {
        mutableStateOf(openingSeason(state, seasons))
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        SeasonsRail(picks, seasons, chosen, Modifier.weight(1f).widthIn(min = SEASONS_MIN_WIDTH)) {
            chosen = it
        }

        // `border-right: 2px solid rgba(107, 114, 128, 0.2)` on `.seasons-pane`,
        // which is a content-box element — the rule really does take two pixels
        // out of the row rather than out of the rail's own width.
        Box(Modifier.width(RAIL_BORDER).fillMaxHeight().background(RAIL_BORDER_COLOR))

        EpisodeRail(picks, episodeRows(state.queue, chosen), Modifier.weight(1f).widthIn(min = EPISODES_MIN_WIDTH))
    }
}

/**
 * The queue, the cursor in it, the words, and what a chosen card does.
 *
 * Five things that only ever travel together, and passing them separately put
 * seven parameters on the card. The list and the index are held together for the
 * reason `ChromeState` holds them together: an index without the list it indexes
 * is a number nobody can check.
 */
internal data class PlaylistPicks(
    val queue: List<TvChromeItem>,
    val currentIndex: Int,
    val strings: MenuStrings,
    val artwork: ChromeArtwork?,
    val cards: CardLayout = LANDSCAPE_CARDS,
    val onPick: (TvChromeItem) -> Unit,
)

// The season the pane opens on: the playing item's, or the lowest there is.
//
// `seasons` is sorted and already excludes specials, so `first()` is the web's
// `seasons[0] ?? 1` without a fallback that cannot happen — the rail is only
// drawn when the rule found two of them.
internal fun openingSeason(state: ChromeState, seasons: List<Int>): Int =
    state.queue.getOrNull(state.queueIndex)?.season?.takeIf { it in seasons } ?: seasons.first()

// `renderSeasonEpisodes` — the items of one season, by their position in the
// WHOLE queue. The position is what a row's tag keys on, so a row keeps its name
// when the season filter changes what is on screen.
internal fun episodeRows(queue: List<TvChromeItem>, season: Int): List<Int> =
    queue.indices.filter { queue[it].season == season }

// The left rail. Season buttons, the current one marked, and choosing one filters
// the right rail rather than navigating anywhere — the web keeps both panes up.
@Composable
private fun SeasonsRail(
    picks: PlaylistPicks,
    seasons: List<Int>,
    chosen: Int,
    modifier: Modifier = Modifier,
    onChoose: (Int) -> Unit,
) {
    ScrollingRail(modifier.testTag(SEASONS_RAIL_TAG), seasons, SEASONS_RAIL_STYLE) { season ->
        MenuRow(seasonLabel(picks.strings, season), isCurrent = season == chosen, tag = "$ROW_SEASON$season") {
            onChoose(season)
        }
    }
}

@Composable
internal fun EpisodeRail(picks: PlaylistPicks, rows: List<Int>, modifier: Modifier) {
    ScrollingRail(modifier.testTag(EPISODES_RAIL_TAG), rows, picks.cards.rail) { index ->
        PlaylistCard(picks, index)
    }
}

/**
 * One rail, scrolling only when there is a height to scroll within.
 *
 *     .scroll-container { overflow-y: auto; padding: 8px 0 8px 8px; width: 100% }
 *
 * A LazyColumn measured with an infinity maximum height does not degrade, it
 * throws — and unbounded is what a test harness and any scrolling parent hand
 * down. So the list is lazy when the rail is bounded and a plain Column when it
 * is not, which is the guard `MenuRows` already uses one level up.
 */
@Composable
private fun ScrollingRail(
    modifier: Modifier,
    values: List<Int>,
    rail: RailStyle,
    row: @Composable (Int) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        if (maxHeight == Dp.Infinity) {
            Column(
                modifier = Modifier.padding(rail.padding),
                verticalArrangement = Arrangement.spacedBy(rail.gap),
            ) {
                values.forEach { row(it) }
            }
        } else {
            val scroll: LazyListState = rememberLazyListState()

            LazyColumn(
                state = scroll,
                contentPadding = rail.padding,
                verticalArrangement = Arrangement.spacedBy(rail.gap),
            ) {
                items(values) { row(it) }
            }

            // The trailing edge is clear because `padding: 8px 0 8px 8px` leaves
            // it clear for this. Without it the list ran past the bottom of the
            // card with nothing to say there was more, and the reserved strip
            // read as a missing margin.
            ScrollRailIndicator(scroll)
        }
    }
}

/**
 * One card, ported from `buildPlaylistCard`.
 *
 *     .playlist-menu-button           { display: flex; gap: 8px; padding: 8px;
 *                                       border-radius: 8px; background: transparent }
 *     .playlist-menu-button:hover     { background: rgba(115, 115, 115, 0.2) }
 *     .playlist-menu-button.is-active { background: rgba(255, 255, 255, 0.1) }
 *
 * `is-active` marks the item playing and hover is what a pointer or a focused
 * remote gets. The web draws them differently because they say different things,
 * so a card collapsing them would stop telling a viewer which episode they are in
 * the middle of.
 */
@Composable
private fun PlaylistCard(picks: PlaylistPicks, index: Int) {
    val item: TvChromeItem = picks.queue.getOrNull(index) ?: return
    var focused: Boolean by remember { mutableStateOf(false) }
    val interaction: MutableInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$ROW_EPISODE$index")
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(cardBackground(index == picks.currentIndex, focused))
            .menuNavEntry(LocalMenuNav.current)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null) { picks.onPick(item) }
            .padding(CARD_PADDING),
        // `gap: 8px`, and 10px in portrait — the one place the two card rules
        // differ about spacing.
        horizontalArrangement = Arrangement.spacedBy(picks.cards.gap),
    ) {
        CardThumbnail(picks, item, index)
        CardText(picks.cards, item, index)
    }
}

// `background: transparent`, and the two states that are not.
private fun cardBackground(active: Boolean, focused: Boolean): Color = when {
    active -> CARD_ACTIVE
    focused -> CARD_HOVER
    else -> Color.Transparent
}

/**
 * The left half.
 *
 *     .episode-menu-button-left { width: 37.5%; flex: 0 0 37.5%; align-self: center;
 *                                 aspect-ratio: 16 / 9; border-radius: 6px;
 *                                 background: rgba(255, 255, 255, 0.05) }
 *
 * `flex: 0 0 37.5%` is a share of the row that neither grows nor shrinks, so it
 * is `fillMaxWidth(0.375f)` rather than a weight: a weight of 0.375 beside the
 * text's weight of 1 makes this 27% of the row instead of 37.5%.
 *
 * The picture itself goes through `ChromeSlots.artwork`, and without it the box
 * keeps the tint the web falls back to when an `img` fails to load.
 */
@Composable
private fun RowScope.CardThumbnail(picks: PlaylistPicks, item: TvChromeItem, index: Int) {
    Box(
        modifier = Modifier
            // `align-self: center`, and `flex-start` in portrait — a taller text
            // block hangs below a top-aligned thumbnail there.
            .align(if (picks.cards.thumbAlignTop) Alignment.Top else Alignment.CenterVertically)
            // Portrait caps the share at 180px; landscape has no cap.
            .rowShare(picks.cards.thumbShare, picks.cards.thumbMaxWidth)
            .aspectRatio(THUMBNAIL_ASPECT)
            .clip(RoundedCornerShape(THUMBNAIL_RADIUS))
            .background(THUMBNAIL_BACKGROUND)
            .testTag("$EPISODE_THUMB_TAG$index"),
    ) {
        item.image?.let { url -> picks.artwork?.invoke(url, Modifier.fillMaxSize()) }

        // `.episode-menu-button-shadow` — inset 0, and what keeps the label and
        // the duration legible over a bright frame.
        Box(Modifier.fillMaxSize().background(THUMBNAIL_SHADOW))

        ThumbnailOverlay(picks.strings, item, index)
    }
}

/**
 * The label, the duration and the progress bar, over the foot of the thumbnail.
 *
 *     .episode-menu-progress-container { position: absolute; bottom: 0; left: 0;
 *                                       right: 0; padding: 0 12px }
 *     .episode-menu-progress-box       { justify-content: space-between;
 *                                       margin-bottom: 4px; padding: 0 4px }
 *     .progress-item-text,
 *     .progress-duration               { font-size: 0.7rem;
 *                                       color: rgba(255, 255, 255, 0.85) }
 */
@Composable
private fun BoxScope.ThumbnailOverlay(strings: MenuStrings, item: TvChromeItem, index: Int) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(horizontal = OVERLAY_PADDING),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = OVERLAY_BOX_PADDING,
                    end = OVERLAY_BOX_PADDING,
                    bottom = OVERLAY_BOX_GAP,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(text = episodeToken(strings, item, index), style = OVERLAY_TEXT, maxLines = 1)
            BasicText(text = durationText(item), style = OVERLAY_TEXT, maxLines = 1)
        }

        // Absent rather than empty without progress. `.slider-container` is
        // `display: none` until `has-progress`, which the web adds only above
        // zero percent — an always-drawn empty track reads as "not started" on
        // every item including the ones nobody has any state for.
        val percent: Int = item.progressPercent ?: 0
        if (percent > 0) {
            ProgressTrack(percent)
        }
    }
}

/**
 *     .slider-container         { background: rgba(107, 114, 128, 0.8); height: 4px;
 *                                 margin: 0 4px 8px; border-radius: 4px }
 *     .slider-container .progress-bar { background: #fff; height: 100% }
 */
@Composable
private fun ProgressTrack(percent: Int) {
    Box(
        modifier = Modifier
            .padding(
                start = OVERLAY_BOX_PADDING,
                end = OVERLAY_BOX_PADDING,
                bottom = TRACK_BOTTOM_MARGIN,
            )
            .fillMaxWidth()
            .height(TRACK_THICKNESS)
            .clip(RoundedCornerShape(TRACK_RADIUS))
            .background(TRACK_COLOR),
    ) {
        // `Math.min(100, percentage)` — a host that stored 103 must not draw a
        // bar wider than its own track.
        Box(
            modifier = Modifier
                .fillMaxWidth(percent.coerceAtMost(FULL_PERCENT) / FULL_PERCENT.toFloat())
                .fillMaxHeight()
                .background(Color.White),
        )
    }
}

/**
 * The right half.
 *
 *     .playlist-card-right           { gap: 4px; flex: 1; min-width: 0 }
 *     .playlist-menu-button-title    { font-weight: 700; line-height: 1.25;
 *                                      -webkit-line-clamp: 2 }
 *     .playlist-menu-button-overview { font-size: 0.7rem; line-height: 1rem;
 *                                      -webkit-line-clamp: 4 }
 *
 * The overview is absent rather than blank on an item without a description, so
 * such a card is the height of its title instead of carrying a gap under it.
 */
@Composable
private fun RowScope.CardText(cards: CardLayout, item: TvChromeItem, index: Int) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
        BasicText(
            // `item.title ?? \`Item ${index + 1}\`` — a row with no name is still
            // a row somebody has to be able to aim at.
            text = item.title?.takeIf { it.isNotBlank() } ?: "${index + 1}",
            // The inherited 13px, and portrait's own `font-size: 0.95rem` with
            // its extra `margin-bottom: 4px` under the flex gap.
            style = TITLE_TEXT.copy(fontSize = cards.titleSize, lineHeight = cards.titleLineHeight),
            maxLines = TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = cards.titleBottomPad),
        )

        item.description?.takeIf { it.isNotBlank() }?.let { overview ->
            BasicText(
                text = overview,
                style = OVERVIEW_TEXT,
                // `-webkit-line-clamp: 4`, and 3 in portrait.
                maxLines = cards.overviewLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// `formatDuration(item.duration)` — the web's display wrapper around
// `formatSeconds`, which answers an empty string rather than "0:00" for a missing
// or non-positive duration. A card reading "0:00" claims the episode is empty.
private fun durationText(item: TvChromeItem): String {
    val seconds: Double = item.durationSeconds ?: return ""

    return if (seconds > 0.0) formatTime(seconds) else ""
}

// ── The numbers, every one off a `.playlist-*` or `.episode-*` rule ───────────
//
// 16px to the rem, which is what the browser computes for this stylesheet: no
// rule in it sets a root font-size, and the card's text inherits the 13px a
// button gets from the user agent — the same 13px `.menu-button-text` measures.

// `.seasons-pane { min-width: 16rem }` and `.episode-menu { min-width: 36rem }`.
internal val SEASONS_MIN_WIDTH = 256.dp
internal val EPISODES_MIN_WIDTH = 576.dp

// `.seasons-pane { border-right: 2px solid rgba(107, 114, 128, 0.2) }` — and in
// portrait the same two pixels turn under the rail as `border-bottom`.
internal val RAIL_BORDER = 2.dp
internal val RAIL_BORDER_COLOR = Color(red = 107, green = 114, blue = 128, alpha = 51)

// `.scroll-container { padding: 8px 0 8px 8px }`.
internal val RAIL_PADDING = PaddingValues(start = 8.dp, top = 8.dp, end = 0.dp, bottom = 8.dp)

// The seasons rail keeps the base scroll-container rule in both orientations —
// the portrait override targets `.episode-menu .scroll-container` alone.
private val SEASONS_RAIL_STYLE = RailStyle(padding = RAIL_PADDING, gap = 0.dp)

// `.playlist-menu-button { padding: 8px; border-radius: 8px }`. The gap moved
// onto CardLayout — it is the one spacing portrait rewrites.
private val CARD_PADDING = 8.dp
private val CARD_RADIUS = 8.dp

// `:hover { rgba(115, 115, 115, 0.2) }` and `.is-active { rgba(255, 255, 255, 0.1) }`.
private val CARD_HOVER = Color(red = 115, green = 115, blue = 115, alpha = 51)
private val CARD_ACTIVE = Color(red = 255, green = 255, blue = 255, alpha = 26)

// `.episode-menu-button-left { aspect-ratio: 16 / 9; border-radius: 6px;
//  background: rgba(255, 255, 255, 0.05) }`. The width share lives on
// CardLayout: 37.5% here, 38% capped at 180px in portrait.
private const val THUMBNAIL_ASPECT = 16f / 9f
private val THUMBNAIL_RADIUS = 6.dp
private val THUMBNAIL_BACKGROUND = Color(red = 255, green = 255, blue = 255, alpha = 13)

// `.episode-menu-button-shadow`, stop for stop:
//
//     linear-gradient(0deg, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.7) 25%,
//                           rgba(0,0,0,0) 50%, rgba(0,0,0,0) 100%)
//
// `0deg` in CSS runs bottom to top, so the stops are written here reversed:
// clear for the top half, 0.7 a quarter up from the foot, 0.85 at the foot. A
// gradient read the way it is written puts the dark end over the picture and the
// clear end under the label, which is the inverse of legible.
//
// Explicit fractions rather than an even four, which is what Compose does with a
// bare colour list — that would put the third stop at 66% and lift the shade a
// sixth of the frame higher than the browser does.
private val THUMBNAIL_SHADOW: Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.5f to Color.Transparent,
    0.75f to Color.Black.copy(alpha = 0.7f),
    1f to Color.Black.copy(alpha = 0.85f),
)

// `.episode-menu-progress-container { padding: 0 12px }`,
// `.episode-menu-progress-box { margin-bottom: 4px; padding: 0 4px }`.
private val OVERLAY_PADDING = 12.dp
private val OVERLAY_BOX_PADDING = 4.dp
private val OVERLAY_BOX_GAP = 4.dp

// `.slider-container { height: 4px; margin: 0 4px 8px; border-radius: 4px;
//  background: rgba(107, 114, 128, 0.8) }`.
private val TRACK_THICKNESS = 4.dp
private val TRACK_RADIUS = 4.dp
private val TRACK_BOTTOM_MARGIN = 8.dp
private val TRACK_COLOR = Color(red = 107, green = 114, blue = 128, alpha = 204)
private const val FULL_PERCENT = 100

// `.playlist-card-right { gap: 4px }`.
private val TEXT_GAP = 4.dp

// `.progress-item-text, .progress-duration { font-size: 0.7rem;
//  color: rgba(255, 255, 255, 0.85) }`.
private val OVERLAY_TEXT = TextStyle(
    color = Color.White.copy(alpha = 0.85f),
    fontSize = 11.2.sp,
)

// `.playlist-menu-button-title { font-weight: 700; line-height: 1.25 }`, clamped
// to two lines. The size comes off CardLayout: the inherited 13px, or portrait's
// own 0.95rem.
private val TITLE_TEXT = TextStyle(
    color = Color.White,
    fontWeight = FontWeight.Bold,
)
private const val TITLE_LINES = 2

// `.playlist-menu-button-overview { font-size: 0.7rem; line-height: 1rem;
//  color: rgba(255, 255, 255, 0.8) }`. The clamp is CardLayout's: four lines,
// three in portrait.
private val OVERVIEW_TEXT = TextStyle(
    color = Color.White.copy(alpha = 0.8f),
    fontSize = 11.2.sp,
    lineHeight = 16.sp,
)
