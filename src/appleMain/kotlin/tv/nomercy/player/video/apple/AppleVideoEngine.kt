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
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.player.PlayerState
import tv.nomercy.player.core.ports.AVPlayerVideoBackend
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.cast.DEFAULT_TV_PORT
import tv.nomercy.player.video.cast.videoCastPlugin

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

    // One per observer, because an application legitimately has more than one.
    //
    // This was a single Job and every call cancelled the one before it, which is
    // a wall rather than guidance: the caller is told nothing, the view that
    // registered first simply stops updating, and what a person sees is a clock
    // stuck at 0:00 over a picture that is plainly playing. The Apple testbed hit
    // it the first time it drew a chrome and a television model from one engine —
    // two honest readers of one state — and only a screenshot caught it.
    //
    // The cost the old rule was protecting against is real: two collectors is two
    // SwiftUI updates per frame. It is also the caller's to spend. A library that
    // silently drops a subscription to save a consumer money is deciding
    // something that was never its decision.
    private val collectors: MutableList<Job> = mutableListOf()

    /**
     * Set up with the defaults.
     *
     * A separate function rather than a default argument, because a Kotlin
     * default argument does not export a no-argument form to Swift -- it
     * exports the one that takes the parameter and marks the empty one
     * unavailable. So a Swift caller wanting the ordinary case would have to
     * spell out every field of PlayerConfig to ask for the values it already
     * has.
     */
    public fun setup() {
        setup(PlayerConfig())
    }

    /**
     * Register a plugin.
     *
     * Here rather than through `plugins.register` directly, because Kotlin
     * generics are invariant across the Objective-C bridge: `Plugin<MessageOptions>`
     * arrives in Swift as something that will not convert to the `Plugin<AnyObject>`
     * the registry asks for, so the call cannot be written on that side at all.
     *
     * Any rather than Plugin<*>, which does not help: a star projection exports
     * as Plugin<AnyObject> too, and Swift will not convert a
     * Plugin<MessageOptions> to it either. The type is checked here instead,
     * and a caller passing something else is told what they passed.
     *
     * The options go to the plugin's own constructor, which is where a Swift
     * caller can pass them with types intact.
     */
    @Suppress("UNCHECKED_CAST")
    public fun addPlugin(plugin: Any) {
        val registrable: Plugin<Any> = plugin as? Plugin<Any>
            ?: error("addPlugin takes a Plugin; got ${plugin::class.simpleName}")
        player.plugins.register(registrable)
    }

    public fun setup(config: PlayerConfig) {
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
     * Every caller gets its own collector and they all run. Registering in a
     * SwiftUI body would therefore accumulate one per redraw — register where the
     * thing that owns the engine is built, and let [dispose] end them.
     */
    public fun observe(onState: (AppleVideoSnapshot) -> Unit) {
        collectors += scope.launch {
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

    /**
     * What can be picked, and picking it.
     *
     * Its own object rather than six more methods on the engine, because it is
     * its own concern: the transport is what a viewer presses and this is what
     * a menu offers. Detekt counted the methods and said so, and it was right —
     * an engine that had grown a track catalogue was an engine doing two jobs.
     */
    public val tracks: AppleVideoTracks = AppleVideoTracks(player)

    /**
     * Registers the cast sender against a television at [host].
     *
     * A method rather than a call Swift makes itself, because `videoCastPlugin`
     * takes a CoroutineScope and Swift has no way to build one. The engine
     * already owns the right one — the same scope every other action runs on,
     * so a cast handoff cannot outlive the player it handed off from.
     *
     * Registering it with nothing on the other end is deliberate and is what
     * the testbed does. Apple's discovery is NoDeviceDiscovery, so there is
     * never a television to find here; the plugin is in the registry either
     * way, its switch works, and every transport action stays local until a
     * controller connects. An absent row would read as a library without
     * casting.
     */
    public fun addCastSender(host: String, port: Int = DEFAULT_TV_PORT) {
        player.plugins.register(videoCastPlugin(host = host, scope = scope, port = port))
    }

    public fun dispose() {
        collectors.forEach(Job::cancel)
        collectors.clear()
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

/**
 * The track surface, read and selected.
 *
 * The lists are read through to the player rather than cached, because Swift
 * matches a menu selection back to the level it stands for. A quality level is
 * a height, a bitrate and a codec with no id of its own, so the option handed
 * to a menu carries a made-up one and the way back is to find the level that
 * still answers to it. Rebuilding a level from the three fields an option
 * carries would drop the codec, the dynamic range and the width, and ask the
 * engine to select something it never offered.
 */
public class AppleVideoTracks internal constructor(private val player: NMVideoPlayer) {

    public fun qualityLevels(): List<QualityLevel> = player.qualityLevels()

    public fun audioTracks(): List<AudioTrack> = player.audioTracks()

    public fun subtitleTracks(): List<SubtitleTrack> = player.subtitles()

    public fun selectQuality(level: QualityLevel?) {
        player.quality(level)
    }

    public fun selectAudio(track: AudioTrack) {
        player.audioTrack(track)
    }

    public fun selectSubtitle(track: SubtitleTrack?) {
        player.subtitle(track)
    }
}

// Zero rather than a division by zero, which is every live stream: the duration
// is unknown and a bar has nothing to fill against.
private fun fractionOf(part: Double, whole: Double): Double =
    if (whole <= 0.0) 0.0 else (part / whole).coerceIn(0.0, 1.0)
