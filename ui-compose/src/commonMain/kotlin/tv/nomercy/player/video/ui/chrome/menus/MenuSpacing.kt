// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

// How far every list inside a menu card sits from its edges, in ONE place.
//
// There were three: ROWS_INSET on the main list, MENU_LIST_PADDING on the
// submenus and RAIL_PADDING on the playlist, and they disagreed. Each was
// written against the web's `.scroll-container { padding: 8px 0 8px 8px }`, and
// each carried the trailing zero that CSS only means because
// `scrollbar-gutter: stable` puts a scrollbar in that strip — so on a phone,
// where nothing stands there, the rows ran flush into the card's rounded corner
// on one side and sat 8dp in on the other.
//
// Three copies of one decision is three chances to fix it in two places, which
// is what happened: the main list and the submenus were corrected and the
// playlist kept its own. One value, referenced everywhere, is the fix — the
// padding was never the bug, the duplication was.
internal val MENU_LIST_INSET = 8.dp

internal val MENU_LIST_PADDING = PaddingValues(
    start = MENU_LIST_INSET,
    top = MENU_LIST_INSET,
    end = MENU_LIST_INSET,
    bottom = MENU_LIST_INSET,
)

// `.scroll-container { gap: 4px }`.
internal val MENU_LIST_GAP = 4.dp
