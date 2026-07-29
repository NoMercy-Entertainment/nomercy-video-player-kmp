// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A control on a television, which is a bigger control.
 *
 * [PlayerIconButton] defaults to the web's 40dp target and 22dp glyph, measured
 * on the running player. Those are right for a pointer and wrong across a room,
 * and this file used to be where the 48/28 lived — for every consumer, including
 * the touch chrome, where eighteen controls a third too wide overflowed the row
 * and pushed ten of them off the screen.
 *
 * So the size moved to the caller and the television says so once, here, rather
 * than at each of its five buttons.
 */
@Composable
internal fun TvIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: (Boolean) -> Unit = {},
) {
    PlayerIconButton(
        icon = icon,
        description = description,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        focusRequester = focusRequester,
        onFocused = onFocused,
        buttonSize = TV_BUTTON_SIZE,
        iconSize = TV_ICON_SIZE,
    )
}
