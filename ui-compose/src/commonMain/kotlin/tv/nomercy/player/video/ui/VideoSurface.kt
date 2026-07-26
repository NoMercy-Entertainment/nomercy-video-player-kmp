// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

// Where the video is drawn, held opaquely.
//
// Every engine renders differently — Media3 attaches to a view, libVLC to a
// native window handle — and none of that belongs in common. Passing the
// surface in rather than reaching into the player for it is what keeps this
// cast-free: the caller already knows which engine it built, so it says so once
// at construction instead of the view guessing at draw time.
public expect class VideoSurface
