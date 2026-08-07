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
 * Keys from the whole window, not only from whatever holds focus.
 *
 * The reference attaches its `keydown` listener to the document by default, so
 * a shortcut works whether or not anything in the player is focused. The port
 * had only `Modifier.onKeyEvent` on the chrome's focusable, which is the
 * reference's `'container'` scope — and the consequence was that clicking any
 * control outside the player took focus away and every shortcut stopped, with
 * nothing to say why.
 *
 * [onKey] returns whether the press was ours. A press we do not want is left
 * with the platform, which is what keeps the window's own shortcuts working.
 *
 * A no-op wherever the platform already delivers keys without focus — Android
 * routes them through the activity before any view sees them.
 */
@Composable
internal expect fun WindowKeyEvents(enabled: Boolean, onKey: (KeyCombo) -> Boolean)
