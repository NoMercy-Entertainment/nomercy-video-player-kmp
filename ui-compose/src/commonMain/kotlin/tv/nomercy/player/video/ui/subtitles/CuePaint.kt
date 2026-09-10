// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

// What the viewer's caption style resolves to, once per layout rather than once
// per cue. The outline is null when the style asks for none.
@Immutable
internal class CuePaint(
    val fill: TextStyle,
    val outline: TextStyle?,
    val textBackground: Color,
    val areaBackground: Color,
)
