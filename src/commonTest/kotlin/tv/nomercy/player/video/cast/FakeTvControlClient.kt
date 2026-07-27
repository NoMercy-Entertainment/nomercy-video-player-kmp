// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

// A television that records what it was told and says what it is told to say.
//
// Both halves matter. The commands going out are what proves a phone became a
// remote control rather than a second renderer; the frames coming back are what
// proves the local interface follows the set rather than its own optimism. A
// fake with only the first half would pass a plugin that never listened.
class FakeTvControlClient(
    var session: RemotePlayerState = RemotePlayerState(),
    var playlist: RemotePlaylist = RemotePlaylist(),
    var server: RemoteServer = RemoteServer(serverName = "Living Room"),
    var launchAccepted: Boolean = true,
) : TvControlClient {

    val commands: MutableList<String> = mutableListOf()

    // Replay of zero: a collector that subscribes late should not be handed a
    // frame from before it was listening, because that is not what a real
    // stream does and a test that relied on it would prove nothing.
    private val frames = MutableSharedFlow<RemoteEvent>(replay = 0, extraBufferCapacity = 64)

    override suspend fun getServer(): RemoteServer {
        commands += "getServer"
        return server
    }

    override suspend fun getSession(): RemotePlayerState {
        commands += "getSession"
        return session
    }

    override suspend fun launch(url: String, autoplay: Boolean, source: String): Boolean {
        commands += "launch:$url:$autoplay:$source"
        return launchAccepted
    }

    override suspend fun play() {
        commands += "play"
    }

    override suspend fun pause() {
        commands += "pause"
    }

    override suspend fun stop() {
        commands += "stop"
    }

    override suspend fun next() {
        commands += "next"
    }

    override suspend fun previous() {
        commands += "previous"
    }

    override suspend fun seek(positionMs: Long) {
        commands += "seek:$positionMs"
    }

    override suspend fun setVolume(level: Double?, delta: Double?, muted: Boolean?) {
        commands += "volume:$level:$delta:$muted"
    }

    override suspend fun setAudioTrack(trackId: String) {
        commands += "audio:$trackId"
    }

    override suspend fun setSubtitleTrack(trackId: String?) {
        commands += "subtitle:$trackId"
    }

    override suspend fun setQuality(level: RemoteQualityLevel) {
        commands += "quality:$level"
    }

    override suspend fun getPlaylist(): RemotePlaylist {
        commands += "getPlaylist"
        return playlist
    }

    override suspend fun setPlaylistActive(index: Int) {
        commands += "playlistActive:$index"
    }

    override fun events(): Flow<RemoteEvent> = frames

    // The set doing something of its own accord: another remote, a viewer at the
    // television itself, the end of a track.
    suspend fun emit(event: RemoteEvent) {
        frames.emit(event)
    }
}
