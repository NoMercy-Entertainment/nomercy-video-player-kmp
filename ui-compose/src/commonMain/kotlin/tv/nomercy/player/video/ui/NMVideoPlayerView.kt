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
import tv.nomercy.player.video.ui.thumbnails.PreviewSprite
import tv.nomercy.player.video.Stretching
import tv.nomercy.player.video.ui.chrome.ChromeLayout
import tv.nomercy.player.video.ui.chrome.rememberChromeState
import tv.nomercy.player.core.device.DeviceCapabilities
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerState
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.chrome.DEFAULT_INACTIVITY_MS
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
    /**
     * The decoded sheet, when the host has one.
     *
     * Separate from [sprite] because the two answer different questions: the cue
     * table says a frame exists, and this one can draw it. loadPreviewSprite
     * builds it and had no caller, so the preview bubble showed a clock over an
     * empty box while the same drag in a browser showed the scene.
     */
    previewSprite: PreviewSprite? = null,
    /**
     * How the bar arranges itself: drop order, portrait-hidden, and the title.
     *
     * Reachable from the drop-in and not only from VideoUiPlugin, because the
     * drop-in is what a consumer writes first. VideoUiOptions grew these three and
     * this view did not, so the same options were expressible through one entry
     * point and invisible through the other.
     */
    layout: ChromeLayout = ChromeLayout(),
    episodes: List<TvEpisode> = emptyList(),
    onClose: () -> Unit = {},
    slots: ChromeSlots = LocalChromeSlots.current,
    surface: VideoSurface? = null,
) {
    // The player's own aspect choice, read the way the chrome reads it — through
    // the projection, so the surface redraws when the ratio is cycled. Passing
    // nothing here is what made setAspectRatio a setter with no effect: it
    // recorded the choice, emitted the event, and the picture never moved.
    val stretching: Stretching = rememberChromeState(player).aspectRatio

    val picture: @Composable () -> Unit = {
        surface?.let { PlayerSurface(it, Modifier.fillMaxSize(), stretching) }
    }

    when (capabilities.formFactor) {
        // Forwarded here for the same reason `layout` had to be: a parameter this
        // took and did not pass on is a parameter that silently does nothing.
        // `previewSprite` reached the touch chrome and stopped at this branch, so
        // a television was the one surface where a scrub showed no picture.
        FormFactor.Tv -> {
            val chrome: TvChrome = rememberTvChrome(player, episodes, onClose, previewSprite = previewSprite)

            NMTvPlayerView(
                controller = chrome.controller,
                transport = chrome.transport,
                content = chrome.content,
                strings = strings,
                modifier = modifier,
                sprite = chrome.sprite,
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
            previewSprite = previewSprite,
            // Forwarded, because a parameter this took and did not pass on is a
            // parameter that silently does nothing — which is what `layout` was.
            layout = layout,
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
