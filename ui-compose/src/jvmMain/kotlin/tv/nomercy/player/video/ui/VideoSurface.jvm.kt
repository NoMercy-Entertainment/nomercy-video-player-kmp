// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import tv.nomercy.player.core.natives.libvlc.VlcMediaPlayer
import tv.nomercy.player.core.ports.FrameSourceBackend
import tv.nomercy.player.core.ports.VideoBackend
import tv.nomercy.player.core.ports.VlcjVideoBackend

/**
 * Where the desktop picture goes, decided before anything plays.
 *
 * The sink is created and attached HERE, when the surface is, rather than by
 * the composable that draws it. That ordering is the whole of it: an engine
 * picks a video output the first time it is told to play, and a callback
 * surface attached after that moment takes effect on the next media rather than
 * this one. The composable mounts after the testbed has already called play, so
 * the attach ran, the log said so, and the engine ignored it — the picture went
 * to a native window and the player pane stayed black.
 *
 * One surface, set once. An earlier attempt attached a placeholder at backend
 * construction and let the composable swap in the real one; swapping a live
 * callback surface segfaults libVLC. There is nothing to swap now.
 *
 * Takes an ENGINE rather than libVLC's player. Both desktop engines decode into
 * a buffer and both hand it over through [FrameSourceBackend], and while this
 * named one of them by type an mpv backend could play with nowhere to draw.
 * [embeddedPlayer] is nullable for that reason and is null on any engine that
 * is not libVLC — a v0 signature change, and the alternative was a second
 * surface class with the same job.
 */
public actual class VideoSurface private constructor(
    public val embeddedPlayer: VlcMediaPlayer?,
    // The engine, so the view can tell it how big the picture is drawn. Told
    // rather than asked: an engine renders into a buffer and has no view to
    // measure.
    internal val backend: VideoBackend?,
    source: FrameSourceBackend?,
) {

    internal val sink: ComposeFrameSink = ComposeFrameSink()

    /** libVLC's own player, for an app that built the surface from one. */
    public constructor(embeddedPlayer: VlcMediaPlayer) : this(embeddedPlayer, null, null)

    /** Any engine that hands its picture to a sink. */
    public constructor(engine: VideoBackend) : this(
        (engine as? VlcjVideoBackend)?.embeddedPlayer,
        engine,
        engine as? FrameSourceBackend,
    )

    init {
        // Through the engine when there is one, because that is the path that
        // works for both. Falling back to the raw player keeps a surface built
        // from one working — it just gets no ladder cap, which is where every
        // desktop was before the engine was passed at all.
        when {
            source != null -> source.videoFrameSink(sink)
            else -> embeddedPlayer?.videoFrameSink(sink)
        }
    }
}
