// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.core.media.Chapter
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.math.abs

// The key contract, bound to an actual player.
//
// Until now the only thing implementing PlayerCommands was a recorder in the
// tests, which meant the binding table was proven and nothing could use it: a
// consumer wanting the shipped keys had to write the whole adapter themselves.
// This is that adapter, and it lives beside the contract rather than in a
// toolkit module because a SwiftUI chrome needs exactly the same one.
//
// The scope is the caller's. Half the transport is suspending and a key press is
// not, so something has to hold the coroutines, and a binding that made its own
// scope would keep running after the screen it belongs to has gone.
public fun playerCommandsOf(player: NMVideoPlayer, scope: CoroutineScope): PlayerCommands =
    PlayerCommands(
        transport = PlayerTransport(player, scope),
        presentation = PlayerPresentation(player, scope),
        volume = PlayerVolume(player, scope),
        state = PlayerStateReader(player),
        window = PlayerWindow(player),
    )

private class PlayerTransport(
    private val player: NMVideoPlayer,
    private val scope: CoroutineScope,
) : VideoTransport {

    override fun togglePlay() {
        scope.launch { player.togglePlayback() }
    }

    override fun play() {
        scope.launch { player.play() }
    }

    override fun pause() {
        scope.launch { player.pause() }
    }

    override fun stop() {
        scope.launch { player.stop() }
    }

    // Through the player's own forward and rewind rather than by computing a
    // target here. They clamp against a duration that may have arrived since,
    // and a chrome doing the arithmetic itself is a seek past the end on a live
    // stream whose duration is still unknown.
    override fun seekBy(deltaSeconds: Float) {
        val magnitude: Double = abs(deltaSeconds.toDouble())

        scope.launch {
            if (deltaSeconds >= 0f) player.forward(magnitude) else player.rewind(magnitude)
        }
    }

    override fun next() {
        scope.launch { player.next() }
    }

    override fun previous() {
        scope.launch { player.previous() }
    }

    override fun nextChapter() {
        scope.launch { player.nextChapter() }
    }

    override fun previousChapter() {
        scope.launch { player.previousChapter() }
    }
}

private class PlayerStateReader(private val player: NMVideoPlayer) : VideoState {

    override fun time(): Double = player.time()

    override fun duration(): Double = player.duration()

    override fun isPlaying(): Boolean = player.playState() == PlayState.PLAYING

    override fun chapters(): List<Chapter> = player.chapters()

    override fun title(): String? = player.item()?.title
}

private class PlayerPresentation(
    private val player: NMVideoPlayer,
    private val scope: CoroutineScope,
) : VideoPresentation {

    override fun cycleSubtitles(): Unit = player.cycleSubtitles()

    override fun cycleAudioTracks(): Unit = player.cycleAudioTracks()

    override fun cycleAspectRatio(): Unit = player.cycleAspectRatio()

    override fun rate(): Float = player.playbackRate().toFloat()

    override fun rate(value: Float) {
        scope.launch { player.playbackRate(value.toDouble()) }
    }

    override fun rates(): List<Float> = player.playbackRates().map { it.toFloat() }

    // Named at the call site and turned into a key here, because the presses
    // this serves are the ones the player has no opinion about: how large the
    // subtitles are belongs to whatever is drawing them.
    override fun emit(name: String) {
        player.emit(EventKey<Unit>(name), Unit)
    }

    override fun message(text: String): Unit = player.message(text)
}

// The window seam, answered by the flag the player keeps. It cannot put itself
// fullscreen — that is the Activity's or the browser's — and this is the record
// of what it was told, which is exactly what Escape has to read.
private class PlayerWindow(private val player: NMVideoPlayer) : VideoWindow {

    override fun toggleFullscreen(): Unit = player.toggleFullscreen()

    override fun isFullscreen(): Boolean = player.fullscreen()
}

private class PlayerVolume(
    private val player: NMVideoPlayer,
    private val scope: CoroutineScope,
) : VideoVolume {

    override fun volumeUp() {
        scope.launch { player.volumeUp() }
    }

    override fun volumeDown() {
        scope.launch { player.volumeDown() }
    }

    override fun toggleMute() {
        scope.launch { player.toggleMute() }
    }
}
