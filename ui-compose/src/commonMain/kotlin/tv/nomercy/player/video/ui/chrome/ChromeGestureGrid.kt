// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The nine tap zones over the picture, as `MobileCenterOverlay` lays them out.
 *
 * A port of the app's own grid rather than a new idea: three rows of three, each
 * cell a third of the picture, and a double tap means something different in
 * each. Brightness up and down the left edge, volume up and down the right,
 * seek either side of the middle, play and pause in the centre. A single tap
 * anywhere shows the controls.
 *
 * The library had one flat tap target for the whole picture, which is why every
 * gesture the app's viewers already use — double-tap to skip, drag the left edge
 * to dim — did nothing here.
 *
 * What this does NOT do is reach for the platform. The app's version reads and
 * writes Android's screen brightness and stream volume directly through
 * `Settings.System` and `AudioManager`, which is correct in an app and
 * impossible in common code: there is no such thing on a desktop and no
 * permission for it on iOS. So the zones report what was asked for and the host
 * decides what that means. A consumer that wants the app's exact behaviour wires
 * [ChromeGestures.onBrightnessDelta] to its own AudioManager call.
 */
@Composable
public fun ChromeGestureGrid(
    gestures: ChromeGestures,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        GestureRow(
            left = gestures.onBrightnessUp,
            centre = null,
            right = gestures.onVolumeUp,
            onTap = gestures.onShowControls,
        )

        // The middle row is the one a thumb lands on without aiming, so it
        // carries the three gestures people use most: skip back, play, skip on.
        GestureRow(
            left = gestures.onSeekBack,
            centre = gestures.onTogglePlay,
            right = gestures.onSeekForward,
            onTap = gestures.onShowControls,
        )

        GestureRow(
            left = gestures.onBrightnessDown,
            centre = null,
            right = gestures.onVolumeDown,
            onTap = gestures.onShowControls,
        )
    }
}

/**
 * What a double tap in each zone asks for.
 *
 * Every one is optional and absent by default, so a host that wants only
 * skip-to-seek gets exactly that and the other seven zones fall through to
 * showing the controls — which is what a single tap does anywhere.
 *
 * Deltas rather than values. The grid does not know what the current brightness
 * is on any platform, and a callback taking an absolute would force every host
 * to hand its state down into a composable that has no use for it.
 */
public data class ChromeGestures(
    val onShowControls: () -> Unit = {},
    val onTogglePlay: (() -> Unit)? = null,
    val onSeekBack: (() -> Unit)? = null,
    val onSeekForward: (() -> Unit)? = null,
    val onBrightnessUp: (() -> Unit)? = null,
    val onBrightnessDown: (() -> Unit)? = null,
    val onVolumeUp: (() -> Unit)? = null,
    val onVolumeDown: (() -> Unit)? = null,
)

// One third of the picture's height, three cells across.
@Composable
private fun ColumnScope.GestureRow(
    left: (() -> Unit)?,
    centre: (() -> Unit)?,
    right: (() -> Unit)?,
    onTap: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
        GestureCell(onDoubleTap = left, onTap = onTap, modifier = Modifier.weight(1f).fillMaxSize())
        GestureCell(onDoubleTap = centre, onTap = onTap, modifier = Modifier.weight(1f).fillMaxSize())
        GestureCell(onDoubleTap = right, onTap = onTap, modifier = Modifier.weight(1f).fillMaxSize())
    }
}

// One cell. Transparent, because it is a target and not a decoration — the app's
// version draws nothing here either and lets the picture through.
@Composable
private fun GestureCell(
    onDoubleTap: (() -> Unit)?,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.pointerInput(onDoubleTap, onTap) {
            detectTapGestures(
                onTap = { onTap() },
                // Null rather than an empty lambda, so a cell with nothing bound
                // does not swallow the second tap waiting for a gesture it will
                // never report.
                onDoubleTap = onDoubleTap?.let { handler -> { _ -> handler() } },
            )
        },
    )
}
