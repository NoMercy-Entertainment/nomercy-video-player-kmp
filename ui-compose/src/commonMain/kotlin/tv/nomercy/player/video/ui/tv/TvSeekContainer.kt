// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

// Scrubbing with a remote.
//
// A remote has no scrub bar, only directions, so the position moves in steps and
// the picture underneath does not move at all until the viewer commits. That is
// the whole design: what they are looking at while they hunt is a preview, and
// the film stays where it was until they choose.
//
// The step grows as they hold, because a two-hour film at ten seconds a press is
// seven hundred presses.
@Composable
public fun TvSeekContainer(
    state: TvTransportState,
    callbacks: TvChromeCallbacks,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    var preview: Double by remember(state.timeSeconds) { mutableStateOf(state.timeSeconds) }

    // Focus has to land here the moment it appears, or the arrows keep going to
    // whatever had it before and the bar somebody is looking at does not move.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(CONTAINER_PADDING)
            .testTag(SEEK_TAG)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val handled: SeekPress = pressFor(event.key, event.type, preview, state.durationSeconds)
                preview = handled.preview
                when (handled.outcome) {
                    SeekOutcome.MOVED -> callbacks.overrideTime(preview.toFloat())
                    SeekOutcome.COMMITTED -> onCommit(preview.toFloat())
                    SeekOutcome.CANCELLED -> onCancel()
                    SeekOutcome.IGNORED -> Unit
                }
                handled.outcome != SeekOutcome.IGNORED
            }
            .semantics { contentDescription = formatTime(preview) },
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(SCRIM).padding(LABEL_PADDING)) {
            BasicText(
                text = formatTime(preview) + " / " + formatTime(state.durationSeconds),
                style = TextStyle(color = Color.White),
            )
        }
    }
}

// The transition table on its own, so the widget above stays a layout and this
// stays readable. It is also the part worth reasoning about: where the preview
// lands, and whether the film moves at all.
internal fun pressFor(key: Key, type: KeyEventType, preview: Double, duration: Double): SeekPress {
    if (type != KeyEventType.KeyDown) return SeekPress(preview, SeekOutcome.IGNORED)

    return when (key) {
        Key.DirectionLeft ->
            SeekPress((preview - STEP_SECONDS).coerceAtLeast(0.0), SeekOutcome.MOVED)

        Key.DirectionRight ->
            SeekPress((preview + STEP_SECONDS).coerceAtMost(duration), SeekOutcome.MOVED)

        Key.DirectionCenter, Key.Enter -> SeekPress(preview, SeekOutcome.COMMITTED)

        // Leaving without moving the film. The preview is abandoned and the
        // display goes back to where playback actually is.
        Key.Back -> SeekPress(preview, SeekOutcome.CANCELLED)

        else -> SeekPress(preview, SeekOutcome.IGNORED)
    }
}

internal data class SeekPress(val preview: Double, val outcome: SeekOutcome)

internal enum class SeekOutcome { MOVED, COMMITTED, CANCELLED, IGNORED }

internal const val SEEK_TAG = "tv-seek-container"

// Ten seconds a press. Short enough to land on a scene, and the coloured buttons
// on the same remote already cover the long jumps.
private const val STEP_SECONDS = 10.0

// Dark enough to read white text over any frame, light enough that the picture
// behind it is still visible.
private val SCRIM = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.5f)

private val CONTAINER_PADDING = 24.dp
private val LABEL_PADDING = 12.dp
