// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.video.tv.TvChromeContent
import tv.nomercy.player.video.tv.TvChromeController
import tv.nomercy.player.video.tv.TvChromeUi
import tv.nomercy.player.video.tv.TvDialog
import tv.nomercy.player.video.tv.TvTransportState

// The whole television chrome, over whatever is drawing the picture.
//
// It owns the state machine and nothing else: the widgets are given state and
// callbacks, the surface is the caller's, and every key that is not the chrome's
// is handed back. That last part is what the implementation this replaces got
// wrong — it consumed presses it had no use for, and a television stopped
// responding to its own remote.
@Composable
public fun NMTvPlayerView(
    controller: TvChromeController,
    transport: TvTransportState,
    content: TvChromeContent,
    strings: TvChromeStrings,
    modifier: Modifier = Modifier,
    onUnhandledKey: (PlayerKey) -> Boolean = { false },
    surface: @Composable () -> Unit = {},
) {
    val ui: TvChromeUi by controller.ui.collectAsState()
    val root: FocusRequester = remember { FocusRequester() }

    // Not a one-shot request. Something else taking focus and not giving it back
    // is a remote that has stopped working, and on a television there is no
    // pointer to recover with. See TvFocusWatchdog.
    var rootHasFocus: Boolean by remember { mutableStateOf(false) }

    TvFocusWatchdog(root) { rootHasFocus }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ROOT_TAG)
            .focusRequester(root)
            .onFocusChanged { rootHasFocus = it.hasFocus }
            .focusable()
            // The chrome first, then the bindings. A press the chrome wants is
            // about what is on screen; anything else is about the film, and one
            // it wants neither of stays with the platform.
            .onKeyEvent { event ->
                val key: PlayerKey? = playerKeyOf(event)
                key != null && (controller.onKey(key) || onUnhandledKey(key))
            },
    ) {
        surface()

        TvChromeLayers(ui, TvScene(controller, transport, content, strings))
    }
}

// The layers over the picture, and which of them is up.
//
// Its own function so the view above stays the wiring: focus, keys, and the
// surface. Splitting them is also what keeps "controls or scrubber, never both"
// readable as one condition rather than three scattered ones.
@Composable
private fun TvChromeLayers(ui: TvChromeUi, scene: TvScene) {
    val watching: Boolean = ui.dialog == TvDialog.None && ui.controlsVisible && !ui.seekMode

    AnimatedVisibility(visible = watching) {
        TvWatchingLayer(scene)
    }

    AnimatedVisibility(visible = ui.seekMode) {
        Box(modifier = Modifier.fillMaxSize().tvSafeArea(), contentAlignment = Alignment.BottomCenter) {
            TvSeekContainer(
                state = scene.transport,
                callbacks = scene.controller.callbacks,
                onCommit = scene.controller::commitSeek,
                onCancel = { scene.controller.cancelSeek() },
            )
        }
    }

    AnimatedVisibility(visible = ui.preScreenVisible) {
        TvPreScreen(
            content = scene.content,
            callbacks = scene.controller.callbacks,
            strings = scene.strings,
            onOpen = scene.controller::openDialog,
            modifier = Modifier.tvSafeArea(),
        )
    }

    TvChromeDialogs(ui.dialog, scene)
}

@Composable
private fun TvWatchingLayer(scene: TvScene) {
    Box(modifier = Modifier.fillMaxSize().tvSafeArea()) {
        TvTopBar(
            item = scene.content.item,
            strings = scene.strings,
            onOpen = scene.controller::openDialog,
            onFocusChanged = scene.controller::setTopBarFocus,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        TvBottomBar(
            state = scene.transport,
            callbacks = scene.controller.callbacks,
            strings = scene.strings,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// The four things every layer needs, carried together.
//
// They are one thing — what the chrome is showing — and passing them as four
// arguments through three functions is where one of them ends up stale in a
// layer nobody looked at.
private data class TvScene(
    val controller: TvChromeController,
    val transport: TvTransportState,
    val content: TvChromeContent,
    val strings: TvChromeStrings,
)

@Composable
private fun TvChromeDialogs(dialog: TvDialog, scene: TvScene) {
    val safeArea: Modifier = Modifier.tvSafeArea()
    val content: TvChromeContent = scene.content
    val strings: TvChromeStrings = scene.strings
    val controller: TvChromeController = scene.controller

    when (dialog) {
        TvDialog.Episodes -> TvEpisodesDialog(
            episodes = content.episodes,
            onSelect = { controller.chooseEpisode(it) },
            title = strings.episodes,
            modifier = safeArea,
        )

        TvDialog.Language -> TvTrackDialog(
            tracks = content.audioTracks,
            onSelect = { controller.chooseAudioTrack(it) },
            title = strings.language,
            tag = AUDIO_TAG,
            modifier = safeArea,
        )

        TvDialog.Subtitle -> TvTrackDialog(
            tracks = content.subtitleTracks,
            onSelect = { controller.chooseSubtitleTrack(it) },
            title = strings.subtitles,
            tag = SUBTITLE_TAG,
            modifier = safeArea,
        )

        TvDialog.None, TvDialog.PreScreen, TvDialog.SubtitleSearch -> Unit
    }
}

internal const val ROOT_TAG = "nm-tv-root"
