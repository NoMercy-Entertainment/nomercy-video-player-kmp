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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// Tapping the picture.
//
// Three columns. The sides are double-tap to skip, the middle is tap to
// play and pause, and a single tap anywhere hides the controls if they were
// showing when the finger landed. That last part is the one rule that cannot be
// decided here: it needs what was on screen at pointer-down, which the chrome
// controller snapshots.
//
// The zones are invisible and cover the whole picture, because on a phone the
// picture is the control surface and a viewer aiming at a target they cannot see
// still hits it every time.
@Composable
public fun TouchZonesOverlay(
    controller: ChromeController,
    commands: ChromeCommands,
    nowMs: () -> Long,
    modifier: Modifier = Modifier,
    options: TouchZonesOptions = TouchZonesOptions(),
) {
    var run: SeekRun by remember { mutableStateOf(SeekRun()) }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Zone(
                tag = ZONE_BACK,
                controller = controller,
                onDoubleTap = {
                    commands.seekBy(-options.seekSeconds)
                    run = run.plus(SeekSide.Back, options.seekSeconds, nowMs(), options.runWindowMs)
                },
            )

            Zone(
                tag = ZONE_CENTRE,
                controller = controller,
                // Fullscreen rather than a bigger skip. The middle of the
                // picture is where a viewer taps to make it fill the screen, and
                // every player they have used does that.
                onDoubleTap = { commands.setFullscreen(true) },
                onSingleTapExtra = { if (!options.disableClickToPause) commands.setPlaying(false) },
            )

            Zone(
                tag = ZONE_FORWARD,
                controller = controller,
                onDoubleTap = {
                    commands.seekBy(options.seekSeconds)
                    run = run.plus(SeekSide.Forward, options.seekSeconds, nowMs(), options.runWindowMs)
                },
            )
        }

        if (run.isVisible(nowMs(), options.runWindowMs)) {
            BasicText(
                text = run.label,
                style = TextStyle(color = Color.White, fontSize = INDICATOR_SIZE),
                modifier = Modifier
                    .align(if (run.side == SeekSide.Back) Alignment.CenterStart else Alignment.CenterEnd)
                    .testTag(if (run.side == SeekSide.Back) INDICATOR_BACK else INDICATOR_FORWARD),
            )
        }
    }
}

// One column of the grid.
//
// The single tap goes through the controller's snapshot rather than reading the
// live state, because by the time a tap resolves the surface has already woken
// the controls and a live read hides what the tap just revealed.
@Composable
private fun androidx.compose.foundation.layout.RowScope.Zone(
    tag: String,
    controller: ChromeController,
    onDoubleTap: () -> Unit,
    onSingleTapExtra: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag(tag)
            .pointerInput(tag) {
                detectTapGestures(
                    onPress = {
                        controller.onTapDown()
                        controller.bumpActivity()
                    },
                    onDoubleTap = { onDoubleTap() },
                    // Handled means the controls were up and this hid them.
                    // Anything else is a tap that revealed them, and acting again
                    // would undo it inside the same gesture.
                    onTap = {
                        val hidTheControls: Boolean = controller.onSingleTap()
                        if (hidTheControls) onSingleTapExtra?.invoke()
                    },
                )
            },
    )
}

public data class TouchZonesOptions(
    val seekSeconds: Float = 10f,
    // How long a run of taps stays one run. Long enough to tap four times
    // deliberately, short enough that a later tap is a new gesture.
    val runWindowMs: Long = 1_000L,
    // Some hosts put their own control on the middle of the picture. Turning
    // this off leaves the tap doing only what the chrome does with it.
    val disableClickToPause: Boolean = false,
)

internal const val ZONE_BACK = "nm-zone-back"
internal const val ZONE_CENTRE = "nm-zone-centre"
internal const val ZONE_FORWARD = "nm-zone-forward"
internal const val INDICATOR_BACK = "nm-seek-indicator-back"
internal const val INDICATOR_FORWARD = "nm-seek-indicator-forward"

private val INDICATOR_SIZE = 24.sp
