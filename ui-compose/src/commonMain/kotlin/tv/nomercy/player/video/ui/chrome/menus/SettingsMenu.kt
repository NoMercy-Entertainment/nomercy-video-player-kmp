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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SCRIM)
            .padding(MENU_PADDING)
            .testTag(SETTINGS_MENU_TAG),
    ) {
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

private val SCRIM = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.9f)
private val MENU_PADDING = 16.dp

// The playlist rails and their rows, tagged so a test can assert which layout
// was drawn rather than counting children.
internal const val SEASONS_RAIL_TAG = "nm-seasons-rail"
internal const val EPISODES_RAIL_TAG = "nm-episodes-rail"
internal const val ROW_SEASON = "nm-season-"
internal const val ROW_EPISODE = "nm-episode-"

// Translated by the host like every other label; the default is the web's.
private const val SEASON_LABEL = "Season"
