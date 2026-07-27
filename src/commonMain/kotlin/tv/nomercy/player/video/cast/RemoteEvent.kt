// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// One frame from the television's event stream, decoded.
//
// Closed, unlike almost everything else here. These are the frames a television
// can send and a consumer adding a case would be describing something that
// cannot arrive — the same reason the wire enums are closed. Unknown is how a
// newer television's frame survives an older phone: named, ignored, not fatal.
public sealed interface RemoteEvent {

    public data class State(val state: RemotePlayerState) : RemoteEvent

    public data class Transport(val transport: String) : RemoteEvent

    public data class Playlist(val playlist: RemotePlaylist) : RemoteEvent

    public data class Track(val audioTrackId: String?, val subtitleTrackId: String?) : RemoteEvent

    public data class Device(val action: String) : RemoteEvent

    public data class Failure(val reason: String) : RemoteEvent

    public data class Unknown(val name: String) : RemoteEvent
}

@Serializable
private data class TransportPayload(val transport: String = "")

@Serializable
private data class TrackPayload(
    val audioTrackId: String? = null,
    val subtitleTrackId: String? = null,
)

@Serializable
private data class DevicePayload(val action: String = "")

@Serializable
private data class FailurePayload(val reason: String = "")

// Decode one frame, or nothing.
//
// A malformed payload yields null rather than throwing. The stream is a long
// connection carrying frames from a device on the other side of a house, and an
// exception here would end the whole subscription over one bad frame — the
// television would still be playing and the phone would have stopped listening.
public fun decodeRemoteEvent(name: String, payload: String, json: Json): RemoteEvent? = runCatching {
    when (name) {
        "state" -> RemoteEvent.State(json.decodeFromString(RemotePlayerState.serializer(), payload))
        "playlist" -> RemoteEvent.Playlist(json.decodeFromString(RemotePlaylist.serializer(), payload))
        "transport" -> RemoteEvent.Transport(
            json.decodeFromString(TransportPayload.serializer(), payload).transport,
        )

        "track" -> json.decodeFromString(TrackPayload.serializer(), payload).let { track ->
            RemoteEvent.Track(track.audioTrackId, track.subtitleTrackId)
        }

        "device" -> RemoteEvent.Device(json.decodeFromString(DevicePayload.serializer(), payload).action)
        "error" -> RemoteEvent.Failure(json.decodeFromString(FailurePayload.serializer(), payload).reason)
        else -> RemoteEvent.Unknown(name)
    }
}.getOrNull()
