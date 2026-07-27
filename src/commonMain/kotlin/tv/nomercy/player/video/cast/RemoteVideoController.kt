// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Whether this phone is talking to a television, and how far it has got.
public enum class RemoteStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

// The television, as one object a chrome can read and drive.
//
// It holds what the set last said and forwards what the viewer asks for, and
// those are two directions rather than one loop: the phone is not the only thing
// that can move this session. Another remote, or someone standing at the set,
// changes it too — so the state here follows the television rather than the last
// button pressed here.
//
// Units are converted at this boundary and nowhere else. The player counts in
// seconds and volume out of a hundred; the television counts in milliseconds and
// volume out of one. Both conversions live here so neither side has to know the
// other exists.
public open class RemoteVideoController(
    private val client: TvControlClient,
    private val scope: CoroutineScope,
) {

    private val currentState = MutableStateFlow<RemotePlayerState?>(null)

    public val state: StateFlow<RemotePlayerState?> = currentState.asStateFlow()

    private val currentStatus = MutableStateFlow(RemoteStatus.DISCONNECTED)

    public val status: StateFlow<RemoteStatus> = currentStatus.asStateFlow()

    private var subscription: Job? = null

    // Ask first, then listen.
    //
    // A phone joining a session already in progress would otherwise show nothing
    // until the set next changed something — which, on a paused film, is however
    // long the viewer stays paused.
    public open suspend fun connect() {
        currentStatus.value = RemoteStatus.CONNECTING
        currentState.value = client.getSession()
        currentStatus.value = RemoteStatus.CONNECTED

        subscription?.cancel()
        subscription = scope.launch {
            client.events().collect(::apply)
        }
    }

    public open fun disconnect() {
        subscription?.cancel()
        subscription = null
        currentStatus.value = RemoteStatus.DISCONNECTED
    }

    // What the set said, folded into what is known.
    //
    // A transport frame carries only the new playback state, and a naive reader
    // would either ignore it or throw the rest of the session away. Neither is
    // right: the film is still the film, and the pause button has to go down
    // now rather than when the next full state arrives.
    protected open fun apply(event: RemoteEvent) {
        when (event) {
            is RemoteEvent.State -> currentState.value = event.state

            is RemoteEvent.Transport -> currentState.value = currentState.value?.copy(
                playbackState = playbackStateOf(event.transport) ?: return,
            )

            is RemoteEvent.Track -> currentState.value = currentState.value?.let { known ->
                known.copy(
                    audioTrackId = event.audioTrackId ?: known.audioTrackId,
                    subtitleTrackId = event.subtitleTrackId ?: known.subtitleTrackId,
                    subtitleEnabled = event.subtitleTrackId != null,
                )
            }

            is RemoteEvent.Playlist -> currentState.value = currentState.value?.copy(
                playlistActiveIndex = event.playlist.activeIndex,
                playlistLength = event.playlist.items.size,
            )

            // The set saying it is done with us — switched off, switched input,
            // or another phone took it. Carrying on would leave a chrome
            // offering control of something that stopped listening.
            is RemoteEvent.Device -> if (event.action == DISCONNECTED) disconnect()

            // Reported upward by the plugin rather than handled here; a failure
            // does not by itself mean the session ended.
            is RemoteEvent.Failure -> Unit

            is RemoteEvent.Unknown -> Unit
        }
    }

    public open suspend fun play() {
        client.play()
    }

    public open suspend fun pause() {
        client.pause()
    }

    public open suspend fun stop() {
        client.stop()
    }

    public open suspend fun next() {
        client.next()
    }

    public open suspend fun previous() {
        client.previous()
    }

    // Seconds in, milliseconds out. The one place that conversion happens.
    public open suspend fun seek(seconds: Double) {
        client.seek((seconds * MILLIS_PER_SECOND).toLong())
    }

    // Out of a hundred in, out of one out — the player's scale and the
    // television's respectively, and neither of them has to learn the other's.
    public open suspend fun setPlaylistActive(index: Int) {
        client.setPlaylistActive(index)
    }

    // What the set plays it with, as its own object.
    //
    // Split out for the same reason the client's interfaces are: a transport bar
    // takes the controller and a settings menu takes this, and neither can reach
    // what the other owns.
    public open val presentation: RemotePresentation = RemotePresentation(client)

    // Null for a word this build does not know. Returning a default would draw
    // a set that is doing something unfamiliar as idle, which is worse than
    // leaving the last known state up until the next frame corrects it.
    private fun playbackStateOf(transport: String): RemotePlaybackState? = when (transport) {
        "playing" -> RemotePlaybackState.PLAYING
        "paused" -> RemotePlaybackState.PAUSED
        "buffering" -> RemotePlaybackState.BUFFERING
        "ended" -> RemotePlaybackState.ENDED
        "idle" -> RemotePlaybackState.IDLE
        else -> null
    }

    private companion object {
        const val DISCONNECTED = "disconnected"
        const val MILLIS_PER_SECOND = 1_000.0
    }
}

// Volume, tracks and quality on the television.
//
// The volume conversion lives here rather than at the client: the player counts
// out of a hundred and the set out of one, and a chrome should be able to hand
// over the number its slider already has.
public open class RemotePresentation(private val client: TvPresentation) {

    public open suspend fun setVolumePercent(percent: Int) {
        client.setVolume(level = percent.coerceIn(0, FULL_PERCENT) / FULL_PERCENT.toDouble())
    }

    public open suspend fun setMuted(muted: Boolean) {
        client.setVolume(muted = muted)
    }

    public open suspend fun setAudioTrack(trackId: String) {
        client.setAudioTrack(trackId)
    }

    // Null turns them off, which is not the same as an empty id.
    public open suspend fun setSubtitleTrack(trackId: String?) {
        client.setSubtitleTrack(trackId)
    }

    public open suspend fun setQuality(level: RemoteQualityLevel) {
        client.setQuality(level)
    }
}

private const val FULL_PERCENT = 100
