// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import tv.nomercy.player.core.ports.FrameSourceBackend
import tv.nomercy.player.core.ports.VideoBackend

/**
 * Where the desktop picture goes, decided before anything plays.
 *
 * The sink is created and attached HERE, when the surface is, rather than by
 * the composable that draws it. That ordering is the whole of it: an engine
 * picks a video output the first time it is told to play, and a sink attached
 * after that moment takes effect on the next media rather than this one. The
 * composable mounts after the testbed has already called play, so the attach
 * ran, the log said so, the engine ignored it, and the pane stayed black.
 *
 * One surface, set once. An earlier attempt attached a placeholder at backend
 * construction and let the composable swap in the real one; swapping a live
 * callback surface segfaults a native engine outright.
 *
 * Takes the ENGINE, not a particular engine's player. That is what let libmpv
 * replace libVLC without this file knowing: both decode into a buffer and both
 * hand it over through [FrameSourceBackend].
 */
public actual class VideoSurface(
    // Kept so the view can tell the engine how big the picture is drawn. Told
    // rather than asked: an engine renders into a buffer and has no view to
    // measure.
    internal val backend: VideoBackend,
) {

    internal val sink: ComposeFrameSink = ComposeFrameSink()

    init {
        (backend as? FrameSourceBackend)?.videoFrameSink(sink)
    }
}
