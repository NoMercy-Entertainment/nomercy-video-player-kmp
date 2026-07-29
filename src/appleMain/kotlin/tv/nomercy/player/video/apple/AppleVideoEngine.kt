// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.apple

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.BufferState
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.player.PlayerState
import tv.nomercy.player.core.ports.AVPlayerVideoBackend
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.NMVideoPlayer

/**
 * Everything the SwiftUI chrome reads, in one value.
 *
 * A snapshot rather than a set of properties Swift polls, because the chrome
 * renders from one consistent picture and a UI that read six getters at six
 * moments can draw a position from one frame beside a duration from another.
 *
 * The track lists are in here even though the Compose adapter reads them off
 * the player instead. Compose can: it is in the same process and the same
 * language, and a getter call costs nothing. Every property read from Swift
 * crosses the Objective-C bridge, so the lists come along once per change
 * rather than four times a frame.
 */
public data class AppleVideoSnapshot(
    val playing: Boolean,
    val buffering: Boolean,
    val timeSeconds: Double,
    val durationSeconds: Double,
    val bufferedFraction: Double,
    val volume: Int,
    val muted: Boolean,
    val rate: Double,
    val queueSize: Int,
    val queueIndex: Int,
    val title: String?,
    val qualityLevels: List<QualityLevel>,
    val activeQuality: QualityLevel?,
    val audioTracks: List<AudioTrack>,
    val activeAudio: AudioTrack?,
    val subtitleTracks: List<SubtitleTrack>,
    val activeSubtitle: SubtitleTrack?,
)

/**
 * The engine, assembled for Apple and handed to Swift as plain calls.
 *
 * This is the piece that was missing. The SwiftUI chrome takes a `VideoEngine`,
 * and the only thing conforming to it anywhere was a fake in the test target —
 * so every Apple gate was green against a stand-in and the real player had
 * never driven the real chrome. The testbed mounted a play/pause view instead,
 * which is why its own screen said there were no plugins to list.
 *
 * A Kotlin facade rather than Swift reaching into the engine directly, and that
 * is a deliberate choice about where the awkwardness goes. `Player.on` is
 * generic over `EventKey<T>` and the state is a `StateFlow`; neither survives
 * the Objective-C bridge in a shape a Swift file wants to write. Collecting on
 * this side costs one file and leaves Swift with a closure.
 *
 * What it does NOT do is decide anything. No visibility rule, no autohide, no
 * track-picking policy — those are in core where both toolkits already share
 * them. This assembles and forwards, so a behaviour that differs between Apple
 * and Compose has exactly one place it could have come from, and it is not
 * here.
 */
public class AppleVideoEngine(
    /**
     * The backend, exposed because the render layer needs its `AVPlayer` and
     * only the caller knows where the picture goes.
     */
    public val backend: AVPlayerVideoBackend = AVPlayerVideoBackend(),
) {

    /**
     * The player itself, so a host can add plugins to it.
     *
     * Public on purpose: `player.addPlugin(...)` is the whole public API of the
     * web trio, and an Apple consumer that could not reach it would have a
     * player with no plugin surface at all — which is precisely the state this
     * class exists to end.
     */
    public val player: NMVideoPlayer = NMVideoPlayer(video = backend)

    public val avPlayer: AVPlayer get() = backend.avPlayer

    // Main, because everything downstream of a snapshot is a SwiftUI update and
    // AVFoundation is main-thread affine besides. A collector on a background
    // dispatcher would hand Swift a value it has to hop threads to use, and the
    // hop is where a frame goes missing.
    private val scope = CoroutineScope(Dispatchers.Main)

    private var collecting: Job? = null

    public fun setup(config: PlayerConfig = PlayerConfig()) {
        scope.launch { player.setup(config) }
    }

    public fun queue(items: List<PlaylistItem>) {
        scope.launch { player.queue(items) }
    }

    public fun load(item: PlaylistItem) {
        scope.launch { player.load(item) }
    }

    /**
     * Report the state now and on every change.
     *
     * Called once. Calling it again replaces the previous collector rather than
     * running two, because two collectors is two SwiftUI updates per frame and
     * the second one is invisible until something is slow.
     */
    public fun observe(onState: (AppleVideoSnapshot) -> Unit) {
        collecting?.cancel()
        collecting = scope.launch {
            player.stateFlow.collect { snapshot: PlayerState ->
                onState(snapshotOf(snapshot))
            }
        }
    }

    public fun play() {
        scope.launch { player.play() }
    }

    public fun pause() {
        scope.launch { player.pause() }
    }

    public fun seek(seconds: Double) {
        scope.launch { player.time(seconds) }
    }

    public fun next() {
        scope.launch { player.next() }
    }

    public fun previous() {
        scope.launch { player.previous() }
    }

    public fun selectQuality(level: QualityLevel?) {
        player.quality(level)
    }

    public fun selectAudio(track: AudioTrack) {
        player.audioTrack(track)
    }

    public fun selectSubtitle(track: SubtitleTrack?) {
        player.subtitle(track)
    }

    public fun dispose() {
        collecting?.cancel()
        collecting = null
        scope.launch { player.dispose() }
    }

    // The same mapping chromeStateOf makes for Compose, deliberately field for
    // field. Two mappings that drift are two players.
    private fun snapshotOf(state: PlayerState): AppleVideoSnapshot = AppleVideoSnapshot(
        playing = state.playState == PlayState.PLAYING,
        // Anything other than idle means the picture is not advancing, which is
        // the one question a spinner answers.
        buffering = state.bufferState != BufferState.IDLE,
        timeSeconds = state.time,
        durationSeconds = state.duration,
        bufferedFraction = fractionOf(state.buffered, state.duration),
        volume = state.volume,
        muted = state.muted,
        rate = state.playbackRate,
        queueSize = state.queueLength,
        queueIndex = state.index,
        title = state.item?.title,
        qualityLevels = player.qualityLevels(),
        activeQuality = player.quality(),
        audioTracks = player.audioTracks(),
        activeAudio = player.audioTrack(),
        subtitleTracks = player.subtitles(),
        activeSubtitle = player.subtitle(),
    )
}

// Zero rather than a division by zero, which is every live stream: the duration
// is unknown and a bar has nothing to fill against.
private fun fractionOf(part: Double, whole: Double): Double =
    if (whole <= 0.0) 0.0 else (part / whole).coerceIn(0.0, 1.0)
