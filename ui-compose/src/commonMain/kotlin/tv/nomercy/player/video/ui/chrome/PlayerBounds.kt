// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect

/**
 * Where the player is on screen, for the things that draw outside it.
 *
 * A Popup is a window of its own, so a tooltip or a scrub bubble is positioned
 * against the WINDOW rather than against the player. On a page where the player
 * is one pane among several that is the wrong edge to stop at: the label of a
 * control near the player's left edge slid out over whatever sits beside it,
 * which the browser never does because the popup is a child of the player
 * element and clipped by it.
 *
 * [Rect.Zero] means nobody measured, and every reader falls back to the window.
 */
internal val LocalPlayerBounds = staticCompositionLocalOf { Rect.Zero }
