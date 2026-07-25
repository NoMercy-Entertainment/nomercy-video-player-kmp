// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import tv.nomercy.player.core.controllers.PlayerContext
import tv.nomercy.player.core.media.QualityDescriptor
import tv.nomercy.player.core.ports.CanonicalBackendEvent
import tv.nomercy.player.core.ports.MediaBackend

// The video half of what the engine says.
//
// Core's bridge turns the engine's transport events into player events. These
// four are the ones only a video player has a name for, and they are why the
// video map exists at all: a music player has nothing useful to say about a
// quality level switching or a picture waiting for data.
//
// Each mapping is one line because the work is in having agreed the names.
public class VideoBackendBridge(private val ctx: PlayerContext) {

    private val handlers: MutableList<Pair<String, (Any?) -> Unit>> = mutableListOf()

    public fun attach(backend: MediaBackend) {
        // Decoded enough to start, which is not the same as playing. A chrome
        // enabling its play button reacts to this rather than to `playing`,
        // because by then the viewer has already waited.
        listen(backend, CanonicalBackendEvent.CAN_PLAY) {
            ctx.emit(VideoEvents.CanPlay, Unit)
        }

        // Out of buffered data mid-playback. Distinct from stalled: waiting is
        // the pipeline being hungry, stalled is the network having stopped
        // feeding it, and a chrome shows different things for the two.
        listen(backend, CanonicalBackendEvent.WAITING) {
            ctx.emit(VideoEvents.Waiting, Unit)
        }

        listen(backend, CanonicalBackendEvent.STALLED) {
            ctx.emit(VideoEvents.Stalled, Unit)
        }
    }

    // Announced by whatever is doing ABR, since only it knows what it picked.
    // Carries the descriptor rather than an index for the same reason the
    // ladder does: an index means nothing after the engine reorders.
    public fun announceLevels(levels: List<QualityDescriptor>) {
        ctx.emit(VideoEvents.Levels, LevelsChange(levels))
    }

    public fun detach(backend: MediaBackend) {
        for ((event, handler) in handlers) backend.off(event, handler)
        handlers.clear()
    }

    private fun listen(backend: MediaBackend, event: String, handler: (Any?) -> Unit) {
        backend.on(event, handler)
        handlers.add(event to handler)
    }
}
