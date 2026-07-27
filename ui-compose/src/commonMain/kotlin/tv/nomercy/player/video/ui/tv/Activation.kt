// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

// Whether this press means "do the thing this control does".
//
// Shared because every focusable control asks it and the answer must not differ
// between them: a remote sends the centre of its pad and a keyboard attached to
// the same box sends enter, and a control that took one and not the other works
// until somebody plugs in a keyboard.
//
// Key-up rather than key-down, so a press held to scroll a list does not also
// activate whatever it lands on.
internal fun isActivation(key: Key, type: KeyEventType): Boolean =
    type == KeyEventType.KeyUp && (key == Key.DirectionCenter || key == Key.Enter)
