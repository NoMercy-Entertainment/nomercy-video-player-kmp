// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.flow.Flow

// Moving what is playing on the television.
//
// Split out because it is what most callers want and all a transport control
// needs: a chrome drawing play, pause and a scrubber depends on this and cannot
// reach a track list or end a session through it.
public interface TvTransport {

    public suspend fun play()

    public suspend fun pause()

    public suspend fun stop()

    public suspend fun next()

    public suspend fun previous()

    // Milliseconds, which is the television's unit. The player counts in seconds
    // and the conversion happens once above this.
    public suspend fun seek(positionMs: Long)
}

// Choosing what the television plays it with.
//
// Volume belongs here rather than with transport: it is a property of the
// presentation, like a subtitle track, and a caller changing one usually offers
// the others in the same menu.
public interface TvPresentation {

    // Any of the three may be null, meaning unchanged. A mute button should not
    // have to know the current level in order to press itself.
    public suspend fun setVolume(level: Double? = null, delta: Double? = null, muted: Boolean? = null)

    public suspend fun setAudioTrack(trackId: String)

    // Null turns subtitles off, which is a different thing from an empty id and
    // is why this is nullable rather than defaulted.
    public suspend fun setSubtitleTrack(trackId: String?)

    public suspend fun setQuality(level: RemoteQualityLevel)
}

// Starting, inspecting and following a session on the television.
public interface TvSession {

    // The unauthenticated handshake. Answers what the set is and what it can do
    // before any credential is offered, which is how a picker can list a
    // television it has never talked to.
    public suspend fun getServer(): RemoteServer

    // What the set is doing right now. The controller seeds itself from this
    // rather than waiting for the first event, so a phone joining a session
    // already in progress shows the right thing immediately instead of blank
    // until something changes.
    public suspend fun getSession(): RemotePlayerState

    // Put something on the television. Returns whether it accepted, because a
    // set can refuse — busy, or asked for something it cannot play — and a
    // caller that assumed success would leave a phone in a casting state with
    // nothing on screen.
    public suspend fun launch(url: String, autoplay: Boolean = true, source: String = CAST_SOURCE): Boolean

    public suspend fun getPlaylist(): RemotePlaylist

    public suspend fun setPlaylistActive(index: Int)

    // The set's own account of what is happening, as it happens.
    //
    // A flow rather than a callback because the subscription's lifetime is the
    // caller's business: a phone that backgrounds should stop listening, and a
    // callback registered somewhere has no obvious moment to be taken back.
    public fun events(): Flow<RemoteEvent>
}

// Everything a phone says to a television, and everything it hears back.
//
// One type to implement and to hand around, three to depend on. A chrome that
// only changes tracks takes TvPresentation and cannot end a session with it,
// which is the same reason the system transport got six verbs rather than the
// whole player.
//
// The seam speaks the domain rather than the protocol: no response objects, no
// status codes, no raw JSON reaching a caller. That is what lets the controller
// above it be proven without a socket or a television, and it is why the library
// can own this transport at all — unlike the music side, where the wire is
// SignalR and the application owns the connection.
public interface TvControlClient : TvTransport, TvPresentation, TvSession

// What the television is told this session came from. It shows the source in
// its own interface, so "cast" is a word a viewer sees rather than a protocol
// detail.
public const val CAST_SOURCE: String = "cast"
