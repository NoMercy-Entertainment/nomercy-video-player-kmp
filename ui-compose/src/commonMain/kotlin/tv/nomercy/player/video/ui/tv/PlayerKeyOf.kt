// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.input.key.KeyEvent
import tv.nomercy.player.core.input.PlayerKey

// The press, in the vocabulary the rest of this understands.
//
// Compose reports a key differently on each platform it runs on, and the mapping
// from a platform key code to a named key already exists in core. This is the
// bridge between the two, and it is per-platform because the thing being read
// out of the event is.
//
// Null for a key the player has no name for, which is most of them and is how
// the press stays with the platform.
internal expect fun playerKeyOf(event: KeyEvent): PlayerKey?
