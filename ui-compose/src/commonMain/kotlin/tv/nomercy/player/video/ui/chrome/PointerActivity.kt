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
internal fun Modifier.pointerActivity(onMove: () -> Unit, onExit: () -> Unit): Modifier =
    pointerInput(onMove, onExit) {
        awaitPointerEventScope {
            while (true) {
                val event: PointerEvent = awaitPointerEvent()

                when (event.type) {
                    PointerEventType.Move, PointerEventType.Enter -> onMove()
                    PointerEventType.Exit -> onExit()
                    else -> Unit
                }
            }
        }
    }
