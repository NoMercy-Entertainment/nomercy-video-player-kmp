// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.nomercy.player.video.Stretching

// The engine's own view, hosted inside Compose.
//
// Neither engine draws into a Compose canvas: Media3 wants a PlayerView in the
// Android view hierarchy and libVLC wants a native window handle. Each actual
// is the bridge to one of those, and the seam is here so the composable above
// never learns which one it got.
//
// [stretching] is how the picture fills the box, and it was absent entirely — so
// setAspectRatio recorded a choice, emitted an event and changed nothing anyone
// could see. The web applies the same four to the video element as object-fit:
// uniform is contain, fill is fill, exactfit is cover, none is none.
@Composable
public expect fun PlayerSurface(
    surface: VideoSurface,
    modifier: Modifier,
    stretching: Stretching = Stretching.Uniform,
)
