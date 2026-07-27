// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import tv.nomercy.player.core.input.PlayerKey

// A desktop reaches the same chrome through a gamepad or the arrow keys. Only
// the ones the chrome itself acts on: everything else belongs to the bindings,
// which read the keyboard directly.
internal actual fun playerKeyOf(event: KeyEvent): PlayerKey? {
    if (event.type != KeyEventType.KeyDown) return null

    return when (event.key) {
        Key.DirectionLeft -> PlayerKey.Left
        Key.DirectionRight -> PlayerKey.Right
        Key.DirectionUp -> PlayerKey.Up
        Key.DirectionDown -> PlayerKey.Down
        Key.DirectionCenter, Key.Enter -> PlayerKey.Center
        Key.Escape, Key.Back -> PlayerKey.Back
        else -> null
    }
}
