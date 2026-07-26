// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayerState

// Ten feet away, where there is no pointer.
//
// On a television the select key IS the play button: a viewer holding a remote
// has no way to aim at a control, so the surface itself takes focus and answers
// the key. This is the minimal form of what the app's TV plugin does with a full
// focus state machine, and it is deliberately a modifier rather than a variant
// of the view — a TV app composes it onto whatever chrome it has, instead of
// choosing between a phone player and a TV player.
//
// Both keys, because they are not the same key. DPAD_CENTER comes off a remote
// and ENTER off a keyboard or an emulator, and a viewer who has one of them and
// not the other still has to be able to pause.
@Composable
public fun Modifier.remoteControl(
    player: ComposedPlayer,
    autoFocus: Boolean = true,
): Modifier = composed {
    val state: State<PlayerState> = player.stateFlow.collectAsState()
    val scope: CoroutineScope = rememberCoroutineScope()
    val requester: FocusRequester = remember { FocusRequester() }

    // Focus has to land somewhere or the first key press goes nowhere and the
    // remote appears dead. A TV app that manages its own focus turns this off.
    LaunchedEffect(autoFocus) {
        if (autoFocus) requester.requestFocus()
    }

    this
        .focusRequester(requester)
        .focusable()
        .onKeyEvent { event ->
            // KeyUp, not KeyDown: a held key repeats, and a repeating toggle
            // flickers a player between playing and paused until the viewer
            // lets go.
            val handled: Boolean = event.type == KeyEventType.KeyUp && event.key in SELECT_KEYS
            if (handled) scope.launch { togglePlayback(player, state.value) }
            handled
        }
}

private val SELECT_KEYS: Set<Key> = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar)
