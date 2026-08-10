// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.VolumeState

// P22b Task 4 — the outbound half TvControlServer calls "events"/"session":
// a snapshot of what the television is doing now, projected from the same
// ComposedPlayer ComposedPlayerReceiverPlayback drives. Named ReceiverState
// rather than "music status" — the plan's own P22b.4 label carries the Cast
// SDK's onStatusUpdated vocabulary, but the projection has nothing
// music-specific about it: it is video-shaped because this library is.
//
// One shape, not four SSE event kinds. TvControlServer's /events splits
// state/transport/playlist/track apart to keep each frame small over a
// heartbeat-driven stream; this is a pure function producing one snapshot
// on demand, and a transport that wants deltas can diff two of them itself
// rather than this seam owning that policy.
public data class ReceiverStateEvent(
    val playbackState: String,
    val positionMs: Long,
    val durationMs: Long,
    val itemId: String?,
    val itemTitle: String?,
    val audioTrackId: String?,
    val subtitleTrackId: String?,
    val qualityLabel: String,
    val volumeLevel: Int,
    val muted: Boolean,
    val playlistLength: Int,
    val playlistActiveIndex: Int,
)

// Read-only: nothing here mutates the player, mirroring how
// projectPlayerState reads TvControlServer's PlayerStore without touching it.
public fun projectReceiverState(player: ComposedPlayer): ReceiverStateEvent {
    val time = player.timeData()
    val quality = player.qualityLevels()
        .filter { it.height > 0 }
        .let { levels ->
            val current = player.quality()
            when {
                current == null -> "auto"
                levels.isEmpty() -> "auto"
                current.height >= HIGH_HEIGHT -> "high"
                current.height >= MEDIUM_HEIGHT -> "medium"
                else -> "low"
            }
        }
    val playlist = player.queue()
    return ReceiverStateEvent(
        playbackState = when (player.playState()) {
            PlayState.PLAYING -> "playing"
            PlayState.LOADING -> "buffering"
            PlayState.PAUSED, PlayState.STOPPED -> "paused"
            PlayState.IDLE, PlayState.ERROR -> "idle"
        },
        positionMs = (time.time * MS_PER_SECOND).toLong().coerceAtLeast(0L),
        durationMs = (time.duration * MS_PER_SECOND).toLong().coerceAtLeast(0L),
        itemId = playlist.getOrNull(player.index())?.id,
        itemTitle = playlist.getOrNull(player.index())?.title,
        audioTrackId = player.audioTrack()?.id,
        subtitleTrackId = player.subtitle()?.id,
        qualityLabel = quality,
        volumeLevel = player.volume(),
        muted = player.volumeState() == VolumeState.MUTED,
        playlistLength = playlist.size,
        playlistActiveIndex = if (playlist.isEmpty()) -1 else player.index(),
    )
}

private const val MS_PER_SECOND: Double = 1000.0
private const val HIGH_HEIGHT: Int = 1080
private const val MEDIUM_HEIGHT: Int = 720
