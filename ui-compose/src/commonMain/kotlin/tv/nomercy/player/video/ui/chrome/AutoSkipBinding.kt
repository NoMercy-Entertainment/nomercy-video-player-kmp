// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * The seek his `ChapterAutoSkipPlugin` performs while auto-skip is on.
 *
 * Keyed on the item so the tracker forgets between them, and driven off the
 * position rather than a timer: the plugin listens to the same time updates, and
 * the chapter under the playhead is the only input either of them has.
 */
@Composable
internal fun AutoSkipBinding(state: ChromeState, commands: ChromeCommands) {
    val tracker: AutoSkipTracker = remember { AutoSkipTracker() }

    LaunchedEffect(state.item) { tracker.forget() }

    LaunchedEffect(state.autoSkipChapters, state.timeSeconds) {
        if (state.autoSkipChapters) {
            val position = SkipPosition(
                durationSeconds = state.durationSeconds,
                currentSeconds = state.timeSeconds,
                index = state.queueIndex,
                playlistSize = state.queueSize,
            )

            tracker.targetFor(state.chapters, position)?.let(commands::seekTo)
        }
    }
}
