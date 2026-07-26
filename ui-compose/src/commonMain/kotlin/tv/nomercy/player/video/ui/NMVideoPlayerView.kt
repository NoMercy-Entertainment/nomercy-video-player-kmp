// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerState

// The drop-in player: a surface with one control over it.
//
// It takes the concrete ComposedPlayer rather than the Player interface, and
// that is a finding rather than a preference: nothing implements Player<E>. The
// interface was authored ahead of the implementation and the two have since
// disagreed — transport is suspending on one and not the other, addPlugin has
// different generics, and getPlugin exists on only one of them. Reconciling
// them is real work with its own gate, and a view quietly widening to an
// interface the player does not implement would hide it.
//
// The whole point of it is that an app can mount a working player before it has
// designed one. It is deliberately not the full chrome — no scrubber, no track
// menus — because a skeleton that grew a feature would stop being the thing you
// can drop in on day one and start being a thing you have to configure.
//
// The control reads stateFlow rather than remembering whether it called play.
// A player that pauses itself — end of item, an interruption, another app
// taking audio focus — leaves a remembered flag lying, and a play button that
// lies about what the player is doing is worse than no button.
@Composable
public fun NMVideoPlayerView(
    player: ComposedPlayer,
    surface: VideoSurface,
    modifier: Modifier = Modifier,
) {
    val state: State<PlayerState> = player.stateFlow.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        PlayerSurface(surface, Modifier.fillMaxSize())
        PlayerControls(player, Modifier.align(Alignment.Center))
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
    val state: State<PlayerState> = player.stateFlow.collectAsState()
    val scope: CoroutineScope = rememberCoroutineScope()

    PlayPauseControl(
        playing = state.value.playState == PlayState.PLAYING,
        onToggle = { scope.launch { togglePlayback(player, state.value) } },
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
