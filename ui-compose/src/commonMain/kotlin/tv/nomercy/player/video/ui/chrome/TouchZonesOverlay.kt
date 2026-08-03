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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.touch.TOUCH_VOLUME_STEP
import tv.nomercy.player.video.touch.TouchAction
import tv.nomercy.player.video.touch.TouchZone
import tv.nomercy.player.video.touch.doubleTapAction
import tv.nomercy.player.video.touch.singleTapAction
import tv.nomercy.player.video.touch.zoneAt

// Tapping the picture.
//
// The geometry and the meaning come from [zoneAt] and its two action functions,
// which are the port of the web's touch-zones plugin. This draws no grid of its
// own: it had one — three full-height columns — and a hand-rolled second model
// is how the volume zones went missing while a complete table of them sat in
// the module next door with nothing drawing it.
//
// One hit test rather than five boxes, for the same reason. Overlapping grid
// areas are what the CSS expresses and what Compose cannot, and the arithmetic
// answers the question the boxes were only approximating.
//
// The zones are invisible and cover the whole picture, because on a phone the
// picture is the control surface and a viewer aiming at a target they cannot see
// still hits it every time.
@Composable
public fun TouchZonesOverlay(
    state: ChromeState,
    controller: ChromeController,
    commands: ChromeCommands,
    nowMs: () -> Long,
    modifier: Modifier = Modifier,
    options: TouchZonesOptions = TouchZonesOptions(),
) {
    var run: SeekRun by remember { mutableStateOf(SeekRun()) }

    // Read through a holder rather than captured, so the detector is built once.
    // Keyed on the state instead, every volume change would tear down the
    // gesture recogniser between the two halves of a double tap.
    val live: State<ChromeState> = rememberUpdatedState(state)

    // The three actions with somewhere here to write stay here; the rest reach
    // only the player and are decided in [perform].
    val act: (TouchAction) -> Unit = { action ->
        val side: SeekSide? = seekSideOf(action)

        when {
            side != null -> {
                commands.seekBy(if (side == SeekSide.Back) -options.seekSeconds else options.seekSeconds)
                run = run.plus(side, options.seekSeconds, nowMs(), options.runWindowMs)
            }

            action == TouchAction.TOGGLE_CONTROLS -> controller.onSingleTap()

            else -> perform(action, live.value, commands, options)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TOUCH_ZONES_TAG)
                .pointerInput(options) {
                    detectTapGestures(
                        onPress = {
                            controller.onTapDown()
                            controller.bumpActivity()
                        },
                        onDoubleTap = { at -> act(doubleTapAction(zoneOf(at, size, options))) },
                        // The controls-hiding half of a single tap is the
                        // controller's, and it is the same call in every zone.
                        // The centre is the one that also does something, which
                        // is what [singleTapAction] says.
                        onTap = { at -> act(singleTapAction(zoneOf(at, size, options))) },
                    )
                },
        )

        SeekIndicatorLayer(run, nowMs, options)
    }
}

// The disc, on the side that was tapped.
//
// Three states, not two. See [seekIndicatorPhase]: it stays composed through its
// fade, because a composable removed the moment it is told to hide never
// finishes fading and pops out of existence instead.
@Composable
private fun BoxScope.SeekIndicatorLayer(
    run: SeekRun,
    nowMs: () -> Long,
    options: TouchZonesOptions,
) {
    val phase: SeekIndicatorPhase = seekIndicatorPhase(run, nowMs(), options)
    if (phase == SeekIndicatorPhase.Gone) return

    SeekIndicator(
        run = run,
        visible = phase == SeekIndicatorPhase.Shown,
        modifier = Modifier
            .align(if (run.side == SeekSide.Back) Alignment.CenterStart else Alignment.CenterEnd)
            // `left: 16px` / `right: 16px`. The disc is pinned to the edge of the
            // picture, not floated in the middle of the third that was tapped.
            .padding(horizontal = EDGE_INSET),
    )
}

// The actions that only have to reach the player, with the state they read.
//
// Every one of them is a TOGGLE, which is what a gesture on a picture means: the
// viewer cannot see a button's state to press the opposite of, so the zone has
// to work it out. The two that were written as `set` — pause, and enter
// fullscreen — were each half a control.
private fun perform(
    action: TouchAction,
    state: ChromeState,
    commands: ChromeCommands,
    options: TouchZonesOptions,
) {
    when (action) {
        // Unconditional. It fired only when the tap had just hidden the controls,
        // and the web source carries a comment about that exact guard being
        // wrong: a cold tap on a phone always finds them hidden, so the middle of
        // the picture silently did nothing.
        TouchAction.TOGGLE_PLAYBACK ->
            if (!options.disableClickToPause) commands.setPlaying(!state.playing)

        TouchAction.TOGGLE_FULLSCREEN -> commands.setFullscreen(!state.fullscreen)

        TouchAction.VOLUME_UP ->
            commands.setVolume((state.volume + TOUCH_VOLUME_STEP).coerceAtMost(MAX_VOLUME))

        TouchAction.VOLUME_DOWN ->
            commands.setVolume((state.volume - TOUCH_VOLUME_STEP).coerceAtLeast(0))

        // Both are handled where the run they write to lives, and so is the one
        // that only reaches the controller. Reached here they would be silent
        // no-ops, so they are stated.
        TouchAction.SEEK_BACK, TouchAction.SEEK_FORWARD, TouchAction.TOGGLE_CONTROLS ->
            error("$action belongs to the overlay, not the player")
    }
}

// Which way a seek goes, or null for an action that is not one.
//
// A lookup rather than two branches at the call site: it is what lets the two
// seeks share the line that stamps the run, and a run stamped in one place
// cannot disagree with itself about when the gesture happened.
private fun seekSideOf(action: TouchAction): SeekSide? = when (action) {
    TouchAction.SEEK_BACK -> SeekSide.Back
    TouchAction.SEEK_FORWARD -> SeekSide.Forward
    else -> null
}

// A tap position as the grid reads it: fractions of the surface, so the same
// arithmetic serves a phone and a desktop window.
//
// A zero-width surface is a layout that has not measured yet, and dividing by it
// would answer with a NaN that lands in whichever zone the comparison happens to
// favour.
private fun zoneOf(at: Offset, size: IntSize, options: TouchZonesOptions): TouchZone {
    if (size.width <= 0 || size.height <= 0) return TouchZone.PLAY_PAUSE

    return zoneAt(
        x = at.x / size.width,
        y = at.y / size.height,
        volumeZones = options.volumeZones,
    )
}

public data class TouchZonesOptions(
    val seekSeconds: Float = 10f,
    // How long a run of taps stays one run. Long enough to tap four times
    // deliberately, short enough that a later tap is a new gesture.
    val runWindowMs: Long = 1_000L,
    /**
     * How long the disc stays on screen after the run has collapsed.
     *
     * The web's second timer: `collapseTimer` at a second resets the figure and
     * arms `hideTimer` for 200ms more. The figure holds still through it, so the
     * control is seen letting go rather than vanishing.
     */
    val hideDelayMs: Long = 200L,
    // Some hosts put their own control on the middle of the picture. Turning
    // this off leaves the tap doing only what the chrome does with it.
    val disableClickToPause: Boolean = false,
    /**
     * Whether the centre column carries volume up and volume down.
     *
     * The web's `_isMobile` branch, and it is an ADDITION rather than a small
     * screen's compromise: a mouse has a wheel and a finger has nothing. This
     * overlay is only mounted where there is no keyboard, so on by default.
     */
    val volumeZones: Boolean = true,
)

internal const val TOUCH_ZONES_TAG = "nm-touch-zones"
internal const val INDICATOR_BACK = "nm-seek-indicator-back"
internal const val INDICATOR_FORWARD = "nm-seek-indicator-forward"

// `.nm-seek-indicator--left { left: 16px }` and its mirror.
private val EDGE_INSET: Dp = 16.dp

private const val MAX_VOLUME = 100
