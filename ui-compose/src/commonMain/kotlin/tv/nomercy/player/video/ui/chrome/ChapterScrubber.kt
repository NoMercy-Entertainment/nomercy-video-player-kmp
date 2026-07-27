// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.video.thumbnails.frameAt
import tv.nomercy.player.video.ui.tv.ChapterProgressBar
import tv.nomercy.player.video.ui.tv.formatTime

// Dragging a finger or a pointer along the film.
//
// The film does not move until the drag ends. What moves while a viewer is
// hunting is a preview, because seeking on every pixel of a drag is a seek storm
// the engine answers by stalling, and the picture they are trying to find never
// settles long enough to be recognised.
//
// The chapter marks come from the same bar the television uses, so a break is in
// the same place on both.
@Composable
public fun ChapterScrubber(
    state: ChromeState,
    commands: ChromeCommands,
    modifier: Modifier = Modifier,
    sprite: List<SpriteCue> = emptyList(),
    onScrubbing: (Boolean) -> Unit = {},
    onPreview: (SpriteCue?) -> Unit = {},
) {
    var dragSeconds: Double? by remember { mutableStateOf(null) }
    var width: Float by remember { mutableStateOf(1f) }

    val shownSeconds: Double = dragSeconds ?: state.timeSeconds

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TOUCH_HEIGHT)
            .testTag(SCRUBBER_TAG)
            // The whole strip is the target, not the few pixels the bar is
            // drawn in. A three-pixel drag target is one nobody hits with a
            // finger, and the drawn height is a design decision rather than a
            // reachability one.
            .pointerInput(state.durationSeconds) {
                width = size.width.toFloat().coerceAtLeast(1f)

                val moveTo: (Float) -> Unit = { x ->
                    dragSeconds = secondsAt(x, width, state.durationSeconds)
                    onPreview(frameAt(sprite, dragSeconds ?: 0.0))
                }
                val finish: (Boolean) -> Unit = { commit ->
                    // Only on a completed drag does the film move. A cancel
                    // leaves it alone, and the preview goes either way or the
                    // bar keeps showing a place nobody went.
                    if (commit) dragSeconds?.let { commands.seekTo(it) }
                    dragSeconds = null
                    onPreview(null)
                    onScrubbing(false)
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        onScrubbing(true)
                        moveTo(offset.x)
                    },
                    onDrag = { change, _ -> moveTo(change.position.x) },
                    onDragEnd = { finish(true) },
                    onDragCancel = { finish(false) },
                )
            }
            .semantics { contentDescription = formatTime(shownSeconds) },
    ) {
        ChapterProgressBar(shownSeconds, state.durationSeconds, state.chapters, Modifier.fillMaxWidth())
    }
}

// Where along the film a horizontal position falls.
//
// Clamped at both ends, because a drag that leaves the strip reports a position
// outside it and an unclamped answer is a seek past the end or before the start.
internal fun secondsAt(x: Float, width: Float, durationSeconds: Double): Double {
    if (durationSeconds <= 0.0 || width <= 0f) return 0.0

    val fraction: Double = (x / width).toDouble().coerceIn(0.0, 1.0)
    return fraction * durationSeconds
}

internal const val SCRUBBER_TAG = "nm-scrubber"

// Taller than the bar it draws. Fingers are not pixels.
private val TOUCH_HEIGHT = 32.dp
