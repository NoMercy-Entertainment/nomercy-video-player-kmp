// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import tv.nomercy.player.core.input.KeyCombo

/**
 * Nothing to install on Android.
 *
 * Presses reach the activity's `dispatchKeyEvent` before any view sees them, so
 * a host that wants them routes them to the player itself — which is what the
 * app already does with a remote's keys. A second global hook here would
 * deliver every press twice.
 */
@Composable
@Suppress("EmptyFunctionBlock") // Nothing to do here is the decision, not an omission.
internal actual fun WindowKeyEvents(enabled: Boolean, onKey: (KeyCombo) -> Boolean) {
}
