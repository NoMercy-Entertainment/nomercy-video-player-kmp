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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import tv.nomercy.player.video.ui.tv.PlayerIconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.tv.sidebarSeasons
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.ui.chrome.ChromeButtons
import tv.nomercy.player.video.ui.chrome.ChromeCommands
import tv.nomercy.player.video.ui.chrome.ChromeState
import tv.nomercy.player.video.ui.chrome.ChromeTranslations
import tv.nomercy.player.video.Stretching
import tv.nomercy.player.video.ui.tv.FluentIcons

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
    //                   flex-direction: column; height: auto }
    //     .main-menu  { min-width: 16rem; max-height: 60vh; border-radius: 8px;
    //                   background: rgba(20, 20, 25, 0.95); gap: 4px }
    //
    // The frame is inset on three sides and `margin-top: auto` pushes the card to
    // the bottom of it, which is what puts the panel above the settings button
    // rather than over the film. 52px of bottom inset is the bar's own height.
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = FRAME_INSET, end = FRAME_INSET, bottom = FRAME_BOTTOM_INSET),
        contentAlignment = Alignment.BottomEnd,
    ) {
        SettingsPanel(strings, menu, onMenuChange) {
            when (menu) {
                MenuState.Main -> MainMenu(state, strings, buttons, onMenuChange)
                MenuState.Quality -> QualityMenu(state, commands, strings, onMenuChange)
                MenuState.Audio -> AudioMenu(state, commands, onMenuChange)
                MenuState.Subtitle -> SubtitleMenu(state, commands, strings, onMenuChange)
                MenuState.Speed -> SpeedMenu(state, commands, strings, onMenuChange)
                MenuState.Playlist -> PlaylistMenu(state, onMenuChange)
                MenuState.AspectRatio -> AspectRatioMenu(state, commands, strings, onMenuChange)
                MenuState.SubtitleSettings -> SubtitleSettingsMenu(state, commands, strings)
                MenuState.AutoSkip -> AutoSkipMenu(state, commands, strings, onMenuChange)
                MenuState.Hidden -> Unit
            }
        }
    }
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
    strings: MenuStrings,
    menu: MenuState,
    onMenuChange: (MenuState) -> Unit,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(min = PANEL_MIN_WIDTH)
            .fillMaxHeight(PANEL_MAX_HEIGHT_SHARE)
            .clip(RoundedCornerShape(PANEL_RADIUS))
            .background(PANEL_BACKGROUND)
            .testTag(SETTINGS_MENU_TAG),
        verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        MenuHeader(strings.titleFor(menu), menu, onMenuChange)

        rows()
    }
}

// The pane's name, a hairline, and the way out.
//
// Back where there is somewhere to go back to, close where there is not: the main
// list's cross dismisses the menu and a pane's arrow returns to the main list,
// which is the web's behaviour and the reason a viewer can get out of the
// subtitle settings without losing the settings menu.
@Composable
private fun MenuHeader(title: String, menu: MenuState, onMenuChange: (MenuState) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HEADER_MIN_HEIGHT)
            .padding(HEADER_PADDING)
            .testTag(MENU_HEADER_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        if (menu != MenuState.Main) {
            PlayerIconButton(
                icon = FluentIcons.Back,
                description = title,
                onClick = { onMenuChange(MenuState.Main) },
                buttonSize = HEADER_BUTTON,
                iconSize = HEADER_ICON,
                modifier = Modifier.testTag(MENU_BACK_TAG),
            )
        }

        BasicText(
            text = title,
            style = TextStyle(color = Color.White, fontSize = HEADER_SIZE, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )

        PlayerIconButton(
            icon = FluentIcons.Close,
            description = title,
            onClick = { onMenuChange(MenuState.Hidden) },
            buttonSize = HEADER_BUTTON,
            iconSize = HEADER_ICON,
            modifier = Modifier.testTag(MENU_CLOSE_TAG),
        )
    }

    // `border-bottom: 1px solid rgba(209, 213, 219, 0.2)`.
    Box(modifier = Modifier.fillMaxWidth().height(HEADER_RULE).background(HEADER_RULE_COLOR))
}

// Which pane a viewer is looking at. The web writes the name into the header, and
// a panel whose header always said "Settings" would be lying three panes deep.
private fun MenuStrings.titleFor(menu: MenuState): String = when (menu) {
    MenuState.Quality -> quality
    MenuState.Audio -> audio
    MenuState.Subtitle -> subtitles
    MenuState.Speed -> speed
    MenuState.Playlist -> playlist
    MenuState.AspectRatio -> aspectRatio
    MenuState.SubtitleSettings -> subtitleSettings
    MenuState.AutoSkip -> autoSkipChapters
    MenuState.Main, MenuState.Hidden -> settings
}

// The episode list, flat or with a seasons rail.
//
// Which one is shouldShowSeasonSidebar's decision, not this composable's: the
// rule excludes specials and movie collections as well as single seasons, and
// re-deriving it here is how the two come to disagree. It reads the answer.
@Composable
private fun PlaylistMenu(state: ChromeState, onMenuChange: (MenuState) -> Unit) {
    val seasons: List<Int> = sidebarSeasons(state.queue)

    Row {
        if (seasons.isNotEmpty()) {
            Column(modifier = Modifier.testTag(SEASONS_RAIL_TAG)) {
                seasons.forEach { season ->
                    MenuRow("$SEASON_LABEL $season", tag = "$ROW_SEASON$season") {
                        onMenuChange(MenuState.Playlist)
                    }
                }
            }
        }

        Column(modifier = Modifier.testTag(EPISODES_RAIL_TAG)) {
            state.queue.forEachIndexed { index, item ->
                MenuRow(item.title.orEmpty(), tag = "$ROW_EPISODE$index") {
                    onMenuChange(MenuState.Hidden)
                }
            }
        }
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

/**
 * The menu's labels for a locale, read from the web's own table.
 *
 * There was no such function, so every consumer got the English defaults below
 * while ChromeTranslations sat in the same package carrying these exact keys in
 * seventy-nine languages. The same failure as the generated icons nobody drew:
 * the translated strings existed and nothing reached for them.
 *
 * Keys are the web's, and the wording with them. "Crop" rather than "Cover"
 * because that is the word a viewer of the browser player has already read.
 */
public fun menuStrings(locale: String): MenuStrings {
    fun menu(name: String): String =
        ChromeTranslations.get(locale, "plugin.desktop-ui.menu.$name")

    return MenuStrings(
        quality = menu("quality"),
        audio = menu("audio"),
        subtitles = menu("subtitles"),
        subtitlesOff = menu("off"),
        speed = menu("speed"),
        automatic = menu("auto"),
        normalSpeed = menu("normal"),
        playlist = menu("playlist"),
        aspectRatio = menu("aspectRatio"),
        aspectOriginal = menu("original"),
        aspectStretch = menu("stretch"),
        aspectCrop = menu("crop"),
        aspectNative = menu("native"),
        settings = menu("settings"),
        subtitleSettings = menu("subtitleSettings"),
        subtitleFont = menu("subtitle.font"),
        subtitleTextSize = menu("subtitle.textSize"),
        subtitleTextColor = menu("subtitle.textColor"),
        subtitleTextOpacity = menu("subtitle.textOpacity"),
        subtitleEdgeStyle = menu("subtitle.edgeStyle"),
        subtitleBackgroundColor = menu("subtitle.backgroundColor"),
        subtitleBackgroundOpacity = menu("subtitle.backgroundOpacity"),
        subtitleAreaColor = menu("subtitle.areaColor"),
        subtitleAreaOpacity = menu("subtitle.areaOpacity"),
        reset = menu("reset"),

        // The auto-skip row's three words are NOT read from the table. The web
        // has no such row, so it has no keys for them, and `get` returns the key
        // itself when it finds nothing — which would put
        // "plugin.desktop-ui.menu.autoSkipChapters" on screen in all 79
        // locales, English included. They stay on the data class, where a host
        // that draws the row overrides them from its own resources, which is
        // where his three already are.
    )
}

public data class MenuStrings(
    val quality: String = "Quality",
    val audio: String = "Audio",
    val subtitles: String = "Subtitles",
    val subtitlesOff: String = "Off",
    val speed: String = "Speed",
    val playlist: String = "Playlist",

    // The aspect menu's labels, which are the web's words rather than its
    // tokens: a viewer reads "Crop", not "exactfit".
    val aspectRatio: String = "Aspect ratio",
    val aspectOriginal: String = "Original",
    val aspectStretch: String = "Stretch",
    val aspectCrop: String = "Crop",
    val aspectNative: String = "Native",

    // The subtitle settings list. One label per property plus the reset, in the
    // web's own words from its menu.subtitle.* keys.
    // The card's own header, which the port had no field for because it had no
    // header. `plugin.desktop-ui.menu.settings` carries it in all 79 locales.
    val settings: String = "Settings",

    val subtitleSettings: String = "Subtitle settings",
    val subtitleFont: String = "Font",
    val subtitleTextSize: String = "Text size",
    val subtitleTextColor: String = "Text color",
    val subtitleTextOpacity: String = "Text opacity",
    val subtitleEdgeStyle: String = "Edge style",
    val subtitleBackgroundColor: String = "Background color",
    val subtitleBackgroundOpacity: String = "Background opacity",
    val subtitleAreaColor: String = "Area color",
    val subtitleAreaOpacity: String = "Area opacity",
    // The auto-skip row and its two options, from his own
    // settings_auto_skip_chapters, player_on and player_off.
    val autoSkipChapters: String = "Auto-skip intros/outros",
    val on: String = "On",
    val off: String = "Off",

    val reset: String = "Reset",
    val automatic: String = "Auto",
    val normalSpeed: String = "Normal",
)

// The rates every player offers. Written here rather than asked of the engine,
// because an engine reports what it can do and this is what a viewer should be
// offered: a list of thirty options is not a menu.
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private const val SDR_WIRE = "sdr"

internal const val SETTINGS_MENU_TAG = "nm-settings-menu"
internal const val MENU_HEADER_TAG = "nm-menu-header"
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

// `min-width: 16rem`.
private val PANEL_MIN_WIDTH = 256.dp

// `max-height: 60vh`, of the player rather than of the window.
private const val PANEL_MAX_HEIGHT_SHARE = 0.6f

private val PANEL_RADIUS = 8.dp
private val PANEL_GAP = 4.dp

// `rgba(20, 20, 25, 0.95)`.
private val PANEL_BACKGROUND = Color(red = 20, green = 20, blue = 25, alpha = 242)

// `min-height: 2.5rem`, `padding: 6px`.
private val HEADER_MIN_HEIGHT = 40.dp
private val HEADER_PADDING = 6.dp
private val HEADER_SIZE = 13.sp
private val HEADER_BUTTON = 28.dp
private val HEADER_ICON = 16.dp

// `border-bottom: 1px solid rgba(209, 213, 219, 0.2)`.
private val HEADER_RULE = 1.dp
private val HEADER_RULE_COLOR = Color(red = 209, green = 213, blue = 219, alpha = 51)

// The playlist rails and their rows, tagged so a test can assert which layout
// was drawn rather than counting children.
internal const val SEASONS_RAIL_TAG = "nm-seasons-rail"
internal const val EPISODES_RAIL_TAG = "nm-episodes-rail"
internal const val ROW_SEASON = "nm-season-"
internal const val ROW_EPISODE = "nm-episode-"

// Translated by the host like every other label; the default is the web's.
private const val SEASON_LABEL = "Season"
