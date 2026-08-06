// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

// A pointer arriving, moving, and leaving.
//
// Compose reports these as event types on the ordinary pointer stream rather
// than as callbacks, and reading them here keeps the whole thing in common code:
// there is no desktop-only modifier involved, so a phone build compiles the same
// source and simply never sees an Exit.
//
// Only the chrome that has a pointer wires this. Attaching it on a touch build
// would hide the controls when a finger lifts, because a lifted finger reports
// an exit exactly like a pointer leaving the window.
internal fun Modifier.pointerActivity(
    onMove: () -> Unit,
    onExit: () -> Unit,
    // Every press, before a child gets it. Clicking a control that is not
    // itself focusable clears focus on Compose Desktop, and a chrome whose key
    // handler hangs off a focused node then hears nothing — every shortcut
    // stops working the moment the viewer touches a button, which is exactly
    // how it was reported.
    onPress: () -> Unit = {},
): Modifier =
    pointerInput(onMove, onExit, onPress) {
        awaitPointerEventScope {
            while (true) {
                // The INITIAL pass, so the press is seen on the way down rather
                // than after a button has consumed it. A consumed press never
                // reaches the Main pass, which is why re-focusing there would
                // fire for every click except the ones that matter.
                val event: PointerEvent = awaitPointerEvent(PointerEventPass.Initial)

                when (event.type) {
                    PointerEventType.Press -> onPress()
                    PointerEventType.Move, PointerEventType.Enter -> onMove()
                    PointerEventType.Exit -> onExit()
                    else -> Unit
                }
            }
        }
    }
