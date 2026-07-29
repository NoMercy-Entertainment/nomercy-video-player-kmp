// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Takes focus back when something else on the screen has taken it.
 *
 * A port of the watchdog in his `TvUiPlugin`, and the difference between it and
 * what was here is the difference between a remote that works and one that has
 * stopped. This asked for focus once, at mount. That is enough right up until a
 * dialog, a toast or a transient node takes it and does not hand it back — after
 * which every press on the remote goes somewhere that is not the player, and
 * from the sofa the picture has simply frozen. There is no pointer to recover
 * with and no obvious way back.
 *
 * Two numbers make it safe to run forever, and both are his:
 *
 *  * [MAX_FRAMES] — how many frames one burst spends asking. Thirty is well over
 *    400ms at 60fps, which is the room a slow ARM box needs to place a node.
 *  * [BACKOFF_MS] — the pause after a burst that never landed. Without it a
 *    subtree that is genuinely unfocusable right now turns this into a
 *    per-frame hot loop for as long as it stays that way.
 *
 * It costs nothing while the player has focus: the loop parks on [hasFocus]
 * going false rather than polling.
 */
@Composable
internal fun TvFocusWatchdog(root: FocusRequester, hasFocus: () -> Boolean) {
    LaunchedEffect(root) {
        // Focus has to start somewhere or the first press goes nowhere.
        runCatching { root.requestFocus() }

        while (true) {
            if (hasFocus()) {
                snapshotFlow(hasFocus).first { !it }
            }

            var attempts = 0
            while (!hasFocus() && attempts < MAX_FRAMES) {
                // A frame at a time, because the node this is asking for may not
                // exist yet — requesting twice inside one frame asks the same
                // absent node twice.
                withFrameNanos {}
                runCatching { root.requestFocus() }
                attempts++
            }

            if (!hasFocus()) delay(BACKOFF_MS)
        }
    }
}

internal const val MAX_FRAMES = 30

internal const val BACKOFF_MS = 500L
