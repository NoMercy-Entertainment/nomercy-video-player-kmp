// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import tv.nomercy.player.core.ports.VlcjVideoBackend
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters

/**
 * Where the desktop picture goes, decided before anything plays.
 *
 * The sink is created and attached HERE, when the surface is, rather than by
 * the composable that draws it. That ordering is the whole of it: libVLC picks
 * a video output the first time it is told to play, and a callback surface
 * attached after that moment takes effect on the next media rather than this
 * one. The composable mounts after the testbed has already called play, so the
 * attach ran, the log said so, and libVLC ignored it — the picture went to a
 * native window and the player pane stayed black.
 *
 * One surface, set once. An earlier attempt attached a placeholder at backend
 * construction and let the composable swap in the real one; swapping a live
 * callback surface segfaults libVLC. There is nothing to swap now.
 */
public actual class VideoSurface(
    public val embeddedPlayer: EmbeddedMediaPlayer,
) {

    internal val sink: ComposeFrameSink = ComposeFrameSink()

    init {
        embeddedPlayer.videoSurface().set(
            CallbackVideoSurface(sink, sink, true, VideoSurfaceAdapters.getVideoSurfaceAdapter()),
        )
    }
}

// The one line an app writes to mount the view on the desktop.
public fun VideoSurface(backend: VlcjVideoBackend): VideoSurface =
    VideoSurface(backend.embeddedPlayer)
