// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import tv.nomercy.player.video.ui.chrome.ChromeButtons
import tv.nomercy.player.video.ui.chrome.ChromeState
import tv.nomercy.player.video.ui.tv.FluentIcons

// The web's category list, in the web's order, with the glyph each row carries.
//
// It was quality, audio, subtitles, speed — four of the seven, and the first two
// the other way round. Order is not decoration in a list somebody navigates with
// a thumb or a d-pad: a viewer who knows audio is the top row on the web reaches
// for the top row here, and reordering it silently is how a remote press picks
// the wrong thing.
//
// Only the lists that have something in them, still. A row that opens onto one
// option is a press that costs a viewer time and gives them no choice.
//
// All seven now. Subtitle settings was the last to land, because it needed the
// nine properties and their choice lists before it had anything to open onto,
// and a row that opens onto nothing is worse than an absent row.
@Composable
internal fun MainMenu(
    state: ChromeState,
    strings: MenuStrings,
    buttons: ChromeButtons,
    onMenuChange: (MenuState) -> Unit,
) {
    MenuRows {
        if (state.audioTracks.size > 1) {
            MenuRow(strings.audio, tag = ROW_AUDIO, icon = FluentIcons.Language, opensSubMenu = true) {
                onMenuChange(MenuState.Audio)
            }
        }

        // Offered whenever the feature is on, even with nothing loaded: turning
        // subtitles off is a choice, and so is finding out there are none.
        MenuRow(strings.subtitles, tag = ROW_SUBTITLE, icon = FluentIcons.Subtitles, opensSubMenu = true) {
            onMenuChange(MenuState.Subtitle)
        }

        MenuRow(
            strings.subtitleSettings,
            tag = ROW_SUBTITLE_SETTINGS,
            icon = FluentIcons.SubtitleSettings,
            opensSubMenu = true,
        ) {
            onMenuChange(MenuState.SubtitleSettings)
        }

        if (state.qualityLevels.size > 1) {
            MenuRow(strings.quality, tag = ROW_QUALITY, icon = FluentIcons.Quality, opensSubMenu = true) {
                onMenuChange(MenuState.Quality)
            }
        }

        MainMenuPresentation(state, strings, onMenuChange)

        // His eighth row, and the web's nothing. Off unless a host asks for it,
        // because the seven above are gated against the web's own list and a
        // library that added one to everybody would fail that check for a good
        // reason.
        if (buttons.autoSkipChapters) {
            MenuRow(
                strings.autoSkipChapters,
                tag = ROW_AUTO_SKIP,
                icon = FluentIcons.ChapterForward,
                opensSubMenu = true,
            ) {
                onMenuChange(MenuState.AutoSkip)
            }
        }
    }
}

// The half of the list that changes how the picture looks rather than what is
// in it. Split from the rows above only because the whole list is longer than
// one function is allowed to be; the order across both is still the web's.
@Composable
private fun MainMenuPresentation(
    state: ChromeState,
    strings: MenuStrings,
    onMenuChange: (MenuState) -> Unit,
) {
    MenuRows {
        MenuRow(strings.speed, tag = ROW_SPEED, icon = FluentIcons.Speed, opensSubMenu = true) {
            onMenuChange(MenuState.Speed)
        }

        MenuRow(
            strings.aspectRatio,
            tag = ROW_ASPECT_RATIO,
            icon = FluentIcons.AspectFit,
            opensSubMenu = true,
        ) {
            onMenuChange(MenuState.AspectRatio)
        }

        // The web lists this here as well as opening it from its own button, and
        // this had only the button. Somebody in the settings list looking for
        // the next episode found nothing.
        if (state.queue.size > 1) {
            MenuRow(strings.playlist, tag = ROW_PLAYLIST, icon = FluentIcons.Playlist, opensSubMenu = true) {
                onMenuChange(MenuState.Playlist)
            }
        }
    }
}

/**
 * The rows, scrollable only when there is a known height to scroll within.
 *
 * A plain Column(verticalScroll) here THREW: "Vertically scrollable component was
 * measured with an infinity maximum height constraints". The panel applies its
 * `heightIn` ceiling only when the parent gave it a bounded height, so under an
 * unbounded one the scroller had no maximum to work against and took the whole menu
 * down with it — an error dialog over the player instead of a settings card.
 *
 * The rows still need to scroll when the card IS bounded, which is every real window
 * and the case Stoney reported. So the scroller is attached on that condition rather
 * than unconditionally, which is the same guard the panel already uses one level up.
 */
@Composable
private fun MenuRows(rows: @Composable () -> Unit) {
    BoxWithConstraints {
        val scrollable: Boolean = maxHeight != Dp.Infinity

        Column(
            modifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier,
        ) {
            rows()
        }
    }
}
