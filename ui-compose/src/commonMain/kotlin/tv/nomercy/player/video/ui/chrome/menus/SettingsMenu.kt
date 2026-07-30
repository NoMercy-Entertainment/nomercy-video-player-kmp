// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.Stretching
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.tv.sidebarSeasons
import tv.nomercy.player.video.ui.chrome.ChromeButtons
import tv.nomercy.player.video.ui.chrome.ChromeCommands
import tv.nomercy.player.video.ui.chrome.ChromeSlots
import tv.nomercy.player.video.ui.chrome.ChromeState
import tv.nomercy.player.video.ui.chrome.LocalChromeSlots

// Which list is open, if any.
//
// One value rather than a boolean per menu. Two open at once is not a state
// anybody designed; it is what happens when five flags are set independently,
// and it is how a viewer ends up choosing a quality from behind a subtitle list.
// Playlist joins the web bar's set. Its own state because the bar opens it
// straight to the episode rail, AND a row inside Main, because the web lists it
// there too — reading only the button was how it ended up reachable one way.
public enum class MenuState {
    Hidden,
    Main,
    Quality,
    Audio,
    Subtitle,
    Speed,
    Playlist,
    AspectRatio,
    SubtitleSettings,
    AutoSkip,
}

// The settings surface: a main list that opens the others.
//
// Every row keys by the descriptor it selects rather than by its position. A
// track list changes when a stream switches rendition, and an index into a list
// that has since changed selects whatever moved into that slot — which is the
// wrong audio language, silently, on exactly the streams that adapt most.
@Composable
public fun SettingsMenu(
    state: ChromeState,
    commands: ChromeCommands,
    menu: MenuState,
    onMenuChange: (MenuState) -> Unit,
    modifier: Modifier = Modifier,
    strings: MenuStrings = MenuStrings(),
    buttons: ChromeButtons = ChromeButtons(),
    slots: ChromeSlots = LocalChromeSlots.current,
) {
    if (menu == MenuState.Hidden) return

    // A panel in the bottom-right corner, not a sheet across the player.
    //
    // This filled the width and grew to whatever height its rows wanted, so the
    // settings list covered the picture and its rows landed on top of the
    // transport — the subtitle pane's "Off" row sat directly over the play
    // button. The web is a card:
    //
    //     .menu-frame { position: absolute; top: 16px; right: 16px; bottom: 52px;
    //                   width: min-content; height: auto;
    //                   max-width: min(52rem, calc(100% - 2rem));
    //                   max-height: calc(100% - 2rem) }
    //     .main-menu  { min-width: 16rem; max-height: 60vh; border-radius: 8px;
    //                   background: rgba(20, 20, 25, 0.95); gap: 4px }
    //
    // The frame is inset on three sides and `margin-top: auto` pushes the card to
    // the bottom of it, which is what puts the panel above the settings button
    // rather than over the film. 52px of bottom inset is the bar's own height.
    //
    // The constraints are read OUTSIDE the insets, which is what makes them the
    // player's own width and height rather than what is left after the padding.
    // `max-width: calc(100% - 2rem)` is a percentage of the player, and the 16px
    // start inset is what enforces it: the card is right-aligned, so it can grow
    // leftwards until 16px from the far edge and no further.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val panel: PanelBox = panelBoxOf(menu, state.queue, maxWidth, maxHeight)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = FRAME_INSET,
                    top = FRAME_INSET,
                    end = FRAME_INSET,
                    bottom = FRAME_BOTTOM_INSET,
                ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            SettingsPanel(panel, MenuHeaderSpec(strings, menu, onMenuChange, state.queue)) {
                when (menu) {
                    MenuState.Main -> MainMenu(state, strings, buttons, onMenuChange)
                    MenuState.Quality -> QualityMenu(state, commands, strings, onMenuChange)
                    MenuState.Audio -> AudioMenu(state, commands, onMenuChange)
                    MenuState.Subtitle -> SubtitleMenu(state, commands, strings, onMenuChange)
                    MenuState.Speed -> SpeedMenu(state, commands, strings, onMenuChange)
                    MenuState.Playlist -> PlaylistPane(state, commands, strings, onMenuChange, slots.artwork)
                    MenuState.AspectRatio -> AspectRatioMenu(state, commands, strings, onMenuChange)
                    MenuState.SubtitleSettings -> SubtitleSettingsMenu(state, commands, strings)
                    MenuState.AutoSkip -> AutoSkipMenu(state, commands, strings, onMenuChange)
                    MenuState.Hidden -> Unit
                }
            }
        }
    }
}

// What the header needs, as one value.
//
// Parameters that only ever travel together, and splitting them made the panel
// take five — which is a threshold telling the truth: the panel's job is the
// card, and the header's identity is one thing. The queue is here because the
// playlist pane's own header names itself from it: "Episodes" over television,
// "Playlist" over a collection of films.
internal data class MenuHeaderSpec(
    val strings: MenuStrings,
    val menu: MenuState,
    val onMenuChange: (MenuState) -> Unit,
    val queue: List<TvChromeItem> = emptyList(),
)

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
 */
internal fun panelBoxOf(
    menu: MenuState,
    queue: List<TvChromeItem>,
    roomWidth: Dp,
    roomHeight: Dp,
): PanelBox {
    val widest: Dp = (roomWidth - FRAME_INSET * 2).coerceAtMost(FRAME_MAX_WIDTH)

    return PanelBox(
        width = contentWidthOf(menu, queue).coerceAtMost(widest).coerceAtLeast(PANEL_MIN_WIDTH),
        maxHeight = if (roomHeight.value.isFinite()) {
            (roomHeight * PANEL_MAX_HEIGHT_SHARE).coerceAtMost(roomHeight - FRAME_INSET * 2)
        } else {
            null
        },
    )
}

internal data class PanelBox(val width: Dp, val maxHeight: Dp?)

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
 * The card itself, and the header the port did not have.
 *
 * `.menu-header` is a real element on the web — 2.5rem tall with a hairline under
 * it, carrying the pane's name and a close cross. Without it a viewer who has
 * opened three panes deep has nothing telling them where they are and no way out
 * except pressing the settings button again.
 *
 * `overflow: hidden` on the card is why the rounding survives a long list, and
 * `max-height: 60vh` is why a forty-episode playlist scrolls instead of growing
 * past the top of the player.
 */
@Composable
private fun SettingsPanel(
    panel: PanelBox,
    header: MenuHeaderSpec,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            // A resolved width, not a minimum.
            //
            // `widthIn(min = 256.dp)` sets a floor and no ceiling, and every row
            // inside fills its width — so the card expanded to the whole player
            // and the bottom-right alignment had nothing left to align. On the web
            // `min-width: 16rem` bounds a flex column of `width: min-content`,
            // whose rows do not stretch. So the width is computed once, from the
            // pane's content and the player's room, and applied — see panelBoxOf.
            .width(panel.width)
            // `height: auto; max-height: 60vh` — a ceiling, not a height.
            //
            // fillMaxHeight(0.6f) made the card 60% tall always, so two rows sat
            // in a panel with a third of a player of empty space under them.
            //
            // Absent when there is no height to take a share OF: a chrome measured
            // unbounded — any test harness, any parent that scrolls — has no 60%
            // to compute, and a scrolling pane handed an infinite ceiling does not
            // degrade, it throws. Unbounded means the card wraps its rows, which
            // is what `height: auto` does when nothing constrains it.
            .then(panel.maxHeight?.let { Modifier.heightIn(max = it) } ?: Modifier)
            .clip(RoundedCornerShape(PANEL_RADIUS))
            .background(PANEL_BACKGROUND)
            .testTag(SETTINGS_MENU_TAG),
        verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        MenuHeader(header)

        // No scroller here, and that is not a shortcut.
        //
        // Several panes are LazyColumns already — the subtitle list, the episode
        // rail — and a LazyColumn inside a Column(verticalScroll) is the nested
        // scroll Compose refuses outright. Wrapping the rows took the whole menu
        // down with it.
        //
        // So the card carries the ceiling and each pane scrolls its own list,
        // which is also the web's shape: `max-height: 60vh` with `overflow:
        // hidden` on `.main-menu`, and the list inside doing the scrolling.
        rows()
    }
}


// The four fittings, in the order the web lists them and the same order the
// button cycles through. A menu that ordered them differently from the button
// would make the same player disagree with itself.
@Composable
private fun AspectRatioMenu(
    state: ChromeState,
    commands: ChromeCommands,
    strings: MenuStrings,
    onMenuChange: (MenuState) -> Unit,
) {
    Column {
        ASPECT_RATIOS.forEach { mode ->
            MenuRow(
                aspectLabel(mode, strings),
                isCurrent = mode == state.aspectRatio,
                tag = "$ROW_ASPECT${mode.token}",
            ) {
                commands.setAspectRatio(mode)
                onMenuChange(MenuState.Hidden)
            }
        }
    }
}

private fun aspectLabel(mode: Stretching, strings: MenuStrings): String = when (mode) {
    Stretching.Uniform -> strings.aspectOriginal
    Stretching.Fill -> strings.aspectStretch
    Stretching.ExactFit -> strings.aspectCrop
    Stretching.None -> strings.aspectNative
}

@Composable
private fun QualityMenu(
    state: ChromeState,
    commands: ChromeCommands,
    strings: MenuStrings,
    onMenuChange: (MenuState) -> Unit,
) {
    LazyColumn {
        // Automatic first, because it is what most viewers should stay on and
        // the list below it exists for the ones who know they want otherwise.
        item {
            MenuRow(strings.automatic, isCurrent = state.activeQuality == null, tag = ROW_AUTO) {
                commands.selectQuality(null)
                onMenuChange(MenuState.Hidden)
            }
        }

        items(state.qualityLevels) { level ->
            MenuRow(qualityLabel(level), isCurrent = level == state.activeQuality) {
                commands.selectQuality(level)
                onMenuChange(MenuState.Hidden)
            }
        }
    }
}

@Composable
private fun AudioMenu(
    state: ChromeState,
    commands: ChromeCommands,
    onMenuChange: (MenuState) -> Unit,
) {
    LazyColumn {
        items(state.audioTracks) { track ->
            MenuRow(audioLabel(track), isCurrent = track == state.activeAudio) {
                commands.selectAudioTrack(track)
                onMenuChange(MenuState.Hidden)
            }
        }
    }
}

@Composable
private fun SubtitleMenu(
    state: ChromeState,
    commands: ChromeCommands,
    strings: MenuStrings,
    onMenuChange: (MenuState) -> Unit,
) {
    LazyColumn {
        // Off is a row rather than an absence. A viewer turning subtitles off
        // has to be able to say so, and a list with no way back is one they
        // leave by restarting the film.
        item {
            MenuRow(strings.subtitlesOff, isCurrent = state.activeSubtitle == null, tag = ROW_SUBTITLE_OFF) {
                commands.selectSubtitleTrack(null)
                onMenuChange(MenuState.Hidden)
            }
        }

        items(state.subtitleTracks) { track ->
            MenuRow(track.label, isCurrent = track == state.activeSubtitle) {
                commands.selectSubtitleTrack(track)
                onMenuChange(MenuState.Hidden)
            }
        }
    }
}

@Composable
private fun SpeedMenu(
    state: ChromeState,
    commands: ChromeCommands,
    strings: MenuStrings,
    onMenuChange: (MenuState) -> Unit,
) {
    LazyColumn {
        items(SPEEDS) { speed ->
            MenuRow(speedLabel(speed, strings), isCurrent = speed == state.rate) {
                commands.setRate(speed)
                onMenuChange(MenuState.Hidden)
            }
        }
    }
}

// Height and dynamic range, because the same height in HDR is a different stream
// a device may not be able to play, and a list showing two identical rows is one
// a viewer cannot choose between.
internal fun qualityLabel(level: QualityLevel): String =
    "${level.height}p" + if (level.dynamicRange.wire == SDR_WIRE) "" else " ${level.dynamicRange.wire}"

// The label the stream gave, because "English" and "English (Commentary)" are
// different tracks somebody chooses between and the language alone hides that.
internal fun audioLabel(track: AudioTrack): String = track.label

internal fun speedLabel(speed: Float, strings: MenuStrings): String =
    if (speed == 1f) strings.normalSpeed else "${speed}x"

// The rates every player offers. Written here rather than asked of the engine,
// because an engine reports what it can do and this is what a viewer should be
// offered: a list of thirty options is not a menu.
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private const val SDR_WIRE = "sdr"

internal const val SETTINGS_MENU_TAG = "nm-settings-menu"
internal const val MENU_HEADER_TAG = "nm-menu-header"
internal const val MENU_ROWS_TAG = "nm-menu-rows"
internal const val MENU_BACK_TAG = "nm-menu-back"
internal const val MENU_CLOSE_TAG = "nm-menu-close"
internal const val ROW_QUALITY = "nm-row-quality"
internal const val ROW_AUDIO = "nm-row-audio"
internal const val ROW_SUBTITLE = "nm-row-subtitle"
internal const val ROW_SUBTITLE_OFF = "nm-row-subtitle-off"
internal const val ROW_SPEED = "nm-row-speed"
internal const val ROW_PLAYLIST = "nm-row-playlist"
internal const val ROW_ASPECT_RATIO = "nm-row-aspect-ratio"
internal const val ROW_ASPECT = "nm-aspect-"
internal const val ROW_SUBTITLE_SETTINGS = "nm-row-subtitle-settings"
internal const val ROW_SUBTITLE_SETTING = "nm-subtitle-setting-"
internal const val ROW_SUBTITLE_RESET = "nm-subtitle-reset"
internal const val ROW_AUTO_SKIP = "nm-row-auto-skip"
internal const val ROW_AUTO_SKIP_ON = "nm-row-auto-skip-on"
internal const val ROW_AUTO_SKIP_OFF = "nm-row-auto-skip-off"
internal const val SUBTITLE_PROPERTY_TAG = "nm-subtitle-property"

// Stretching.entries would read the same and is not: the enum's order is the
// cycle order and this is the menu's, and pinning it here means a value added
// to the enum has to be given a label rather than appearing unlabelled.
private val ASPECT_RATIOS = listOf(
    Stretching.Uniform,
    Stretching.Fill,
    Stretching.ExactFit,
    Stretching.None,
)
internal const val ROW_AUTO = "nm-row-auto"

// Read off .menu-frame, .main-menu and .menu-header on the running player.
//
// The card used to be a full-width black sheet: SCRIM was black at 0.9 and the
// only geometry was 16dp of padding, so the settings list covered the picture and
// its rows landed on the transport.
private val FRAME_INSET = 16.dp

// The bar's own height. `bottom: 52px` is what lifts the card clear of it.
private val FRAME_BOTTOM_INSET = 52.dp

// `.main-menu { min-width: 16rem }` — the FLOOR of the card, which the port used
// as its whole width. See panelBoxOf for what the rest of the rule is.
private val PANEL_MIN_WIDTH = 256.dp

// `.menu-frame { max-width: min(52rem, calc(100% - 2rem)) }`. 52rem is not an
// arbitrary ceiling: it is exactly 16rem of seasons plus 36rem of episodes, so
// the widest pane the player has fits it and nothing is allowed past it.
private val FRAME_MAX_WIDTH = 832.dp

// `max-height: 60vh`, of the player rather than of the window.
private const val PANEL_MAX_HEIGHT_SHARE = 0.6f

private val PANEL_RADIUS = 8.dp

// `rgba(20, 20, 25, 0.95)`.
private val PANEL_BACKGROUND = Color(red = 20, green = 20, blue = 25, alpha = 242)

// The playlist rails and their rows, tagged so a test can assert which layout
// was drawn rather than counting children.
internal const val SEASONS_RAIL_TAG = "nm-seasons-rail"
internal const val EPISODES_RAIL_TAG = "nm-episodes-rail"
internal const val ROW_SEASON = "nm-season-"
internal const val ROW_EPISODE = "nm-episode-"
