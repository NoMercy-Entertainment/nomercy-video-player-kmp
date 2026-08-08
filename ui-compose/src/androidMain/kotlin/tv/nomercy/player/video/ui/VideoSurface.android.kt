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
import tv.nomercy.player.core.ports.FrameSourceBackend
import tv.nomercy.player.core.ports.VideoBackend

/**
 * Where the Android picture goes, decided before anything plays.
 *
 * Two shapes now, because Android has two engines. Media3 renders itself into a
 * view and is handed over as a player; libmpv decodes into a buffer and is
 * handed a sink, exactly as it is on the desktop. Which one a device gets is
 * the registry's decision — ExoPlayer unless the file is one Android has no
 * decoder for.
 *
 * The sink is attached HERE, when the surface is built, and not by the
 * composable that draws it. An engine picks its video output the first time it
 * is told to play, so a sink attached after that takes effect on the next file
 * rather than this one — which on the desktop was a black pane with every log
 * line saying the attach had happened.
 */
public actual class VideoSurface internal constructor(
    internal val backend: VideoBackend?,
    public val exoPlayer: ExoPlayer?,
) {

    internal val sink: ComposeFrameSink? =
        (backend as? FrameSourceBackend)?.let { source ->
            ComposeFrameSink().also(source::videoFrameSink)
        }
}

// The one line an app writes to mount the view, for either engine. Nobody has
// to learn which one they were given, which is the point of the registry.
public fun VideoSurface(backend: VideoBackend): VideoSurface = VideoSurface(
    backend = backend,
    exoPlayer = (backend as? ExoPlayerVideoBackend)?.exoPlayer,
)

// Kept because apps and tests hold an ExoPlayer directly, from before there was
// a second engine to choose between.
public fun VideoSurface(exoPlayer: ExoPlayer): VideoSurface =
    VideoSurface(backend = null, exoPlayer = exoPlayer)
