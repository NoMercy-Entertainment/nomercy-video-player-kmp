// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// What a NoMercy television is doing, as it describes itself over /v1.
//
// Every field has a default, and that is the compatibility rule rather than a
// convenience: a television running an older build sends a frame missing the
// fields it does not know about, and a phone that threw on those would refuse to
// control a set that works perfectly well. Self-hosted installs update on their
// own schedule and the two ends are routinely different ages.

// Closed on purpose. These are wire values rather than an extensible API, and a
// consumer adding a case would be inventing a state the television cannot send.
@Serializable
public enum class RemotePlaybackState {
    @SerialName("idle")
    IDLE,

    @SerialName("buffering")
    BUFFERING,

    @SerialName("playing")
    PLAYING,

    @SerialName("paused")
    PAUSED,

    @SerialName("ended")
    ENDED,
}

@Serializable
public enum class RemoteItemKind {
    @SerialName("movie")
    MOVIE,

    @SerialName("episode")
    EPISODE,

    @SerialName("track")
    TRACK,

    @SerialName("none")
    NONE,
}

@Serializable
public enum class RemoteQualityLevel {
    @SerialName("auto")
    AUTO,

    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,
}

// One television session, whole.
//
// The same shape arrives two ways — as the body of a session request and as the
// payload of an SSE state frame — because they answer the same question and a
// second shape for the second route is a second thing to keep in step.
@Serializable
public data class RemotePlayerState(
    val sessionId: String = "",
    val playbackState: RemotePlaybackState = RemotePlaybackState.IDLE,
    // Milliseconds, which is the television's unit. The player's is seconds and
    // the conversion happens once, at the controller.
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val itemId: String = "",
    val itemKind: RemoteItemKind = RemoteItemKind.NONE,
    val itemTitle: String = "",
    val audioTrackId: String = "",
    val subtitleTrackId: String = "",
    val subtitleEnabled: Boolean = false,
    val qualityLevel: RemoteQualityLevel = RemoteQualityLevel.AUTO,
    val volume: Double = 1.0,
    val muted: Boolean = false,
    val playlistActiveIndex: Int = 0,
    val playlistLength: Int = 0,
)

@Serializable
public data class RemotePlaylist(
    val items: List<RemotePlaylistItem> = emptyList(),
    val activeIndex: Int = 0,
)

@Serializable
public data class RemotePlaylistItem(
    val id: String = "",
    val kind: String = "",
    val title: String = "",
    val durationMs: Long = 0,
    val posterUrl: String = "",
)
