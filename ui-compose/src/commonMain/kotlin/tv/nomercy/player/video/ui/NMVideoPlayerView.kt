// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.core.device.DeviceCapabilities
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerState
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.chrome.rememberChromeStrings
import tv.nomercy.player.video.ui.chrome.ChromeButtons
import tv.nomercy.player.video.ui.chrome.VideoUiKind
import tv.nomercy.player.video.ui.chrome.ChromeSlots
import tv.nomercy.player.video.ui.chrome.LocalChromeSlots
import tv.nomercy.player.video.ui.chrome.VideoChrome
import tv.nomercy.player.video.ui.tv.NMTvPlayerView
import tv.nomercy.player.video.ui.tv.TvChrome
import tv.nomercy.player.video.ui.tv.TvChromeStrings
import tv.nomercy.player.video.tv.TvEpisode
import tv.nomercy.player.video.ui.tv.rememberTvChrome

// The drop-in player: everything, routed by what the device is.
//
// This is the whole public surface an application needs. It mounts the picture,
// the chrome that suits the screen, and the input that screen has, and an app
// that writes this one line gets a player a viewer can use. Nothing below it is
// hidden — every piece it assembles is public and can be taken on its own — but
// nobody should have to.
//
// The routing is on the capability contract rather than on a build flavour,
// because the two clients this replaces each decided what a device was for
// themselves and disagreed: the same tablet was a phone in one and a television
// in the other. One answer, asked of the platform, used everywhere.
@Composable
public fun NMVideoPlayerView(
    player: NMVideoPlayer,
    modifier: Modifier = Modifier,
    capabilities: DeviceCapabilities = rememberDeviceCapabilities(),
    strings: TvChromeStrings = rememberChromeStrings(),
    /**
     * Which of the two players this is.
     *
     * The NoMercy client has a full player over a film and a compact one over a
     * trailer on a detail page, and they differ by which controls they offer.
     * Selecting that here rather than by hand-assembling a ChromeButtons is
     * what makes the trailer reachable from the drop-in at all — it was only
     * expressible through VideoUiPlugin, so nothing that mounted this view
     * could ask for it.
     */
    kind: VideoUiKind = VideoUiKind.Full,
    buttons: ChromeButtons = ChromeButtons.forKind(kind),
    sprite: List<SpriteCue> = emptyList(),
    episodes: List<TvEpisode> = emptyList(),
    onClose: () -> Unit = {},
    slots: ChromeSlots = LocalChromeSlots.current,
    surface: VideoSurface? = null,
) {
    val picture: @Composable () -> Unit = { surface?.let { PlayerSurface(it, Modifier.fillMaxSize()) } }

    when (capabilities.formFactor) {
        FormFactor.Tv -> {
            val chrome: TvChrome = rememberTvChrome(player, episodes, onClose)

            NMTvPlayerView(
                controller = chrome.controller,
                transport = chrome.transport,
                content = chrome.content,
                strings = strings,
                modifier = modifier,
                surface = picture,
            )
        }

        // A tablet is a phone with more room, not a third chrome. The controls
        // are the same size under a finger either way, and a layout that spread
        // them out would put the transport further from the thumb holding it.
        FormFactor.Phone, FormFactor.Tablet, FormFactor.Desktop -> VideoChrome(
            player = player,
            formFactor = capabilities.formFactor,
            modifier = modifier,
            strings = strings,
            buttons = buttons,
            sprite = sprite,
            onClose = onClose,
            slots = slots,
            surface = picture,
        )
    }
}

// The control layer on its own, without a surface.
//
// Split out because it is the half that can be measured: a gate can drive this
// against a real player and watch the engine change, where the surface needs an
// engine holding a real window and is proven by looking at it. The split is not
// a testing convenience — an app replacing the chrome and keeping the surface
// takes exactly this seam.
@Composable
public fun PlayerControls(player: ComposedPlayer, modifier: Modifier = Modifier) {
    val state: PlayerState by player.stateFlow.collectAsState()
    val scope: CoroutineScope = rememberCoroutineScope()

    PlayPauseControl(
        playing = state.playState == PlayState.PLAYING,
        onToggle = { scope.launch { togglePlayback(player, state) } },
        modifier = modifier,
    )
}

// One decision in one place, so the button, the D-pad and the remote cannot
// disagree about what "toggle" means.
public suspend fun togglePlayback(player: ComposedPlayer, state: PlayerState) {
    if (state.playState == PlayState.PLAYING) player.pause() else player.play()
}

// The engine's own picture of itself, for a caller that wants the toggle
// without the view.
@Composable
public fun rememberIsPlaying(player: ComposedPlayer): Boolean {
    val state: PlayerState by player.stateFlow.collectAsState()
    return state.playState == PlayState.PLAYING
}
