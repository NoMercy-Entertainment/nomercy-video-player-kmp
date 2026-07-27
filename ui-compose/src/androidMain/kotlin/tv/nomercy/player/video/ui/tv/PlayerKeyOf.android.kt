// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.core.input.defaultInputAdapter

// Key-down only. A remote sends both down and up, and acting on each would seek
// twice for every press.
internal actual fun playerKeyOf(event: KeyEvent): PlayerKey? {
    if (event.type != KeyEventType.KeyDown) return null

    return defaultInputAdapter().toPlayerKey(event.nativeKeyEvent.keyCode)
}
