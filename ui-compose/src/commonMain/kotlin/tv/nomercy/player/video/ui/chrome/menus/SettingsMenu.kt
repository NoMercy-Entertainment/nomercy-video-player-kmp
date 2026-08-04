// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.layout.PaddingValues
import tv.nomercy.player.video.ui.chrome.trimRate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.core.ports.displayLanguage
import tv.nomercy.player.video.Stretching
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.ui.chrome.ChromeButtons
import tv.nomercy.player.video.ui.chrome.ChromeCommands
import tv.nomercy.player.video.ui.chrome.ChromeSlots
import tv.nomercy.player.video.ui.chrome.ChromeState
import tv.nomercy.player.video.ui.chrome.LocalChromeSlots
import tv.nomercy.player.video.ui.rememberDeviceCapabilities
import tv.nomercy.player.core.ports.DynamicRange

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
        // The web reads `(orientation: portrait)` off the window and writes it
        // onto the container as `data-orientation`; the player's own box is the
        // nearest thing this composable can measure.
        val portrait: Boolean = isPortrait(maxWidth, maxHeight)
        val panel: PanelBox = panelBoxOf(menu, state.queue, maxWidth, maxHeight, portrait)

        Box(
            modifier = Modifier.fillMaxWidth().padding(panelInsets(panel)),
            contentAlignment = Alignment.BottomEnd,
        ) {
            SettingsPanel(panel, MenuHeaderSpec(strings, menu, onMenuChange, state.queue)) {
                when (menu) {
                    MenuState.Main -> MainMenu(state, strings, buttons, onMenuChange)
                    MenuState.Quality -> QualityMenu(state, commands, strings, onMenuChange)
                    MenuState.Audio -> AudioMenu(state, commands, onMenuChange)
                    MenuState.Subtitle -> SubtitleMenu(state, commands, strings, onMenuChange)
                    MenuState.Speed -> SpeedMenu(state, commands, strings, onMenuChange)
                    MenuState.Playlist ->
                        PlaylistPane(state, commands, strings, onMenuChange, slots.artwork, portrait)
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
    // One walk per pane. Rebuilt on a pane switch so the rows of the last pane
    // cannot linger on it, which is `querySelectorAll` run fresh per press.
    val nav: MenuNav = remember(header.menu) { MenuNav() }
    val keyboard: Boolean = LocalMenuKeyboard.current
    val returnFocus: MenuReturnFocus = LocalMenuReturnFocus.current

    // `dialog.show()` — focus lands on the pane's first button, and again on a
    // pane switch, where the browser drops focus on the floor when the pane a
    // row lived in disappears. Landing it on the header keeps the arrows alive.
    LaunchedEffect(nav, keyboard) { if (keyboard) nav.focusHeader() }

    // `dialog.close()` — focus goes back to whoever opened the menu, however the
    // menu went: Escape, the close cross, or a row that picked something.
    DisposableEffect(returnFocus) { onDispose { returnFocus.restore() } }

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
            .then(panelHeight(panel))
            .clip(RoundedCornerShape(panel.radius))
            .background(PANEL_BACKGROUND)
            // Preview rather than bubble, so an open menu answers Up, Down and
            // Escape before the player's own bindings do — the web stops
            // propagation on the frame for exactly this.
            .onPreviewKeyEvent { event ->
                nav.onMenuKey(event) { header.onMenuChange(MenuState.Hidden) }
            }
            .testTag(SETTINGS_MENU_TAG),
        verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        CompositionLocalProvider(LocalMenuNav provides nav) {
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
    Column(
        modifier = Modifier.padding(MENU_LIST_PADDING),
        verticalArrangement = Arrangement.spacedBy(MENU_LIST_GAP),
    ) {
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
    val offerable: List<QualityLevel> =
        offerableRungs(state.qualityLevels, rememberDeviceCapabilities().hasHdrDisplay)

    LazyColumn(
        contentPadding = MENU_LIST_PADDING,
        verticalArrangement = Arrangement.spacedBy(MENU_LIST_GAP),
    ) {
        // Automatic first, because it is what most viewers should stay on and
        // the list below it exists for the ones who know they want otherwise.
        item {
            MenuRow(
                strings.automatic,
                isCurrent = state.qualityAuto,
                tag = ROW_AUTO,
                subLabel = autoQualitySubLabel(state),
            ) {
                commands.selectQuality(null)
                onMenuChange(MenuState.Hidden)
            }
        }

        items(offerable) { level ->
            MenuRow(qualityLabel(level), isCurrent = !state.qualityAuto && level == state.activeQuality) {
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
    LazyColumn(
        contentPadding = MENU_LIST_PADDING,
        verticalArrangement = Arrangement.spacedBy(MENU_LIST_GAP),
    ) {
        itemsIndexed(state.audioTracks) { index, track ->
            MenuRow(audioLabel(track, index), isCurrent = track == state.activeAudio) {
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
    LazyColumn(
        contentPadding = MENU_LIST_PADDING,
        verticalArrangement = Arrangement.spacedBy(MENU_LIST_GAP),
    ) {
        // Off is a row rather than an absence. A viewer turning subtitles off
        // has to be able to say so, and a list with no way back is one they
        // leave by restarting the film.
        item {
            MenuRow(strings.subtitlesOff, isCurrent = state.activeSubtitle == null, tag = ROW_SUBTITLE_OFF) {
                commands.selectSubtitleTrack(null)
                onMenuChange(MenuState.Hidden)
            }
        }

        itemsIndexed(state.subtitleTracks) { index, track ->
            MenuRow(subtitleLabel(track, index), isCurrent = track == state.activeSubtitle) {
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
    LazyColumn(
        contentPadding = MENU_LIST_PADDING,
        verticalArrangement = Arrangement.spacedBy(MENU_LIST_GAP),
    ) {
        items(SPEEDS) { speed ->
            MenuRow(speedLabel(speed, strings), isCurrent = speed == state.rate, tag = "$ROW_SPEED_VALUE$speed") {
                commands.setRate(speed)
                onMenuChange(MenuState.Hidden)
            }
        }
    }
}

// Height and dynamic range, because the same height in HDR is a different stream
// a device may not be able to play, and a list showing two identical rows is one
// a viewer cannot choose between.
// The rung the engine settled on, shown beside Auto as `.menu-button-subtext`.
// Nothing named it, so a viewer in Auto had no way to see what they were
// actually watching.
// The label the stream gave, because "English" and "English (Commentary)" are
// different tracks somebody chooses between and the language alone hides that.
/**
 * What a language row is called, which is four answers deep in the web:
 *
 *     audioTrack.label
 *       ?? languageDisplayName(audioTrack.language, uiLanguage)
 *       ?? audioTrack.language
 *       ?? `Track ${i + 1}`
 *
 * This was `track.label` and stopped at the first, which cannot reach the other
 * three - and the desktop backend already collapses them, defaulting a label to
 * the language CODE and an unlabelled track to the literal "und". So a viewer
 * chose between rows called "und" where the browser offers "Nederlands" and
 * "Track 2".
 */
internal fun audioLabel(track: AudioTrack, index: Int): String =
    trackLabel(track.label, track.language, index)

internal fun subtitleLabel(track: SubtitleTrack, index: Int): String =
    trackLabel(track.label, track.language, index)

// The chain itself, shared because a subtitle row and an audio row are the same
// question about two track types - and because the two panes had drifted, one
// reading `label` through a helper and the other reading it directly.
private fun trackLabel(label: String?, language: String?, index: Int): String {
    val named: String? = label?.takeIf { it.isNotBlank() && !it.equals(UNKNOWN_LANGUAGE, ignoreCase = true) }
    if (named != null && !named.equals(language, ignoreCase = true)) return named

    val tag: String? = language?.takeIf { it.isNotBlank() && !it.equals(UNKNOWN_LANGUAGE, ignoreCase = true) }
    val spelled: String? = tag?.let { displayLanguage(it).takeIf { name -> name.isNotBlank() } }

    return spelled ?: tag ?: "Track ${index + 1}"
}

// libVLC's answer for a track that declares no language, and the value the
// desktop mapper substitutes for a blank one. A row titled "und" is a row a
// viewer cannot choose between two of.
private const val UNKNOWN_LANGUAGE = "und"

// `rate === 1 ? t('menu.normal') : `${rate}×`` — the multiplication sign, and a whole
// rate without its decimal. This wrote the letter x and rendered 2f as "2.0x", so the
// menu row and the transport button disagreed about the same number in two ways.
internal fun speedLabel(speed: Float, strings: MenuStrings): String =
    if (speed == 1f) strings.normalSpeed else "${trimRate(speed)}×"

// The rates every player offers. Written here rather than asked of the engine,
// because an engine reports what it can do and this is what a viewer should be
// offered: a list of thirty options is not a menu.
// The web's own list: `player.playbackRates?.() ?? [0.5, 0.75, 1, 1.25, 1.5, 2]`.
// This carried an extra 1.75 that the web never offers — an invented option, which is
// the one kind of divergence a viewer cannot report because nothing looks broken.
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)


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
internal const val ROW_SPEED_VALUE = "nm-speed-"
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

// The frame and card geometry — FRAME_INSET, PANEL_MIN_WIDTH and the rest —
// lives in PanelBox.kt beside the rule that spends it.

// `rgba(20, 20, 25, 0.95)`.
private val PANEL_BACKGROUND = Color(red = 20, green = 20, blue = 25, alpha = 242)

// The playlist rails and their rows, tagged so a test can assert which layout
// was drawn rather than counting children.
internal const val SEASONS_RAIL_TAG = "nm-seasons-rail"
internal const val EPISODES_RAIL_TAG = "nm-episodes-rail"
internal const val ROW_SEASON = "nm-season-"
internal const val ROW_EPISODE = "nm-episode-"
internal const val EPISODE_THUMB_TAG = "nm-episode-thumb-"

// `.scroll-container { padding: 8px 0 8px 8px; gap: 4px }`.
//
// Every submenu list was a bare LazyColumn with neither. The main list got the
// inset and the lists it opens onto did not, so a subtitle or quality row ran
// edge to edge into a card with an 8px radius while the row above it sat 8px
// in — which is the margin Stoney has filed against these menus more than once.
//
// Padded on BOTH sides, and that is a deliberate divergence from the web.
//
// `scrollbar-gutter: stable` reserves the trailing strip for a scrollbar, so a
// browser's rows stop short of the right edge only because something else is
// standing there. On a phone nothing is, and the rows ran flush into the card's
// rounded corner while the left side sat 8px in — the same list looking
// different on two platforms for a reason neither platform shows.
//
// One inset on both sides is the same UI everywhere. The rail indicator draws
// inside it rather than in a gutter of its own.
private val MENU_LIST_PADDING =
    PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)

private val MENU_LIST_GAP = 4.dp
