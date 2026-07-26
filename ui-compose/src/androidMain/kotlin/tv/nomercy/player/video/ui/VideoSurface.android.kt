// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.media3.exoplayer.ExoPlayer
import tv.nomercy.player.core.ports.ExoPlayerVideoBackend

// Media3 renders through a view that is handed the engine itself.
public actual class VideoSurface(public val exoPlayer: ExoPlayer)

// The one line an app writes to mount the view on Android, so nobody has to
// learn that the render target lives on the backend.
public fun VideoSurface(backend: ExoPlayerVideoBackend): VideoSurface =
    VideoSurface(backend.exoPlayer)
