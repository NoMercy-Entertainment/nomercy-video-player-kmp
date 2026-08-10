// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.MediaBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A recording backend, same shape NMVideoPlayerTest already trusts — this
// proves the wire from ReceiverCommand to the real player, not a second
// opinion on what the engine does with what it receives.
private class RecordingReceiverBackend : MediaBackend {
    val loaded: MutableList<String> = mutableListOf()
    var playCount: Int = 0
    var pauseCount: Int = 0
    val seekedTo: MutableList<Double> = mutableListOf()
    private var time: Double = 0.0
    private val listeners = mutableMapOf<String, MutableList<(Any?) -> Unit>>()

    override suspend fun load(url: String, opts: LoadOptions) {
        loaded += url
    }
    override suspend fun play() { playCount += 1 }
    override fun pause() { pauseCount += 1 }
    override fun stop() = Unit
    override fun release() = Unit
    override fun currentTime(): Double = time
    override fun currentTime(seconds: Double) { seekedTo += seconds; time = seconds }
    override fun duration(): Double = 0.0
    override fun volume(): Float = 1.0f
    override fun volume(value: Float) = Unit
    override fun mute() = Unit
    override fun unmute() = Unit
    override fun buffered(): Double = 0.0
    override fun playbackRate(): Double = 1.0
    override fun playbackRate(rate: Double) = Unit
    override fun state(): BackendState = BackendState.IDLE
    override fun on(event: String, fn: (Any?) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(fn)
    }
    override fun off(event: String, fn: (Any?) -> Unit) { listeners[event]?.remove(fn) }
}

private data class Clip(override val id: String, override val url: String, override val title: String? = null) :
    PlaylistItem

class ComposedPlayerReceiverPlaybackTest {

    // Same reasoning PlayerTransportCommandsTest gives: the adapter launches
    // rather than awaits because a real transport callback arrives off the
    // player's own coroutine, so the eager dispatcher is what lets the
    // assertion read the result in the line right after the call.
    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun aLaunchCommandLoadsTheUrlOnTheRealPlayer() = runTest {
        val backend = RecordingReceiverBackend()
        val player = ComposedPlayer(backend = backend, scope = backgroundScope)
        player.setup(PlayerConfig())
        val playback = ComposedPlayerReceiverPlayback(player, eager())

        playback.apply(ReceiverCommand.Launch("https://example.test/a.mkv"))

        assertEquals(listOf("https://example.test/a.mkv"), backend.loaded)
    }

    @Test
    fun playAndPauseReachTheBackendThroughTheRealTransport() = runTest {
        val backend = RecordingReceiverBackend()
        val player = ComposedPlayer(backend = backend, scope = backgroundScope)
        player.setup(PlayerConfig())
        player.queue(listOf(Clip("a", "https://example.test/a.mkv")))
        val playback = ComposedPlayerReceiverPlayback(player, eager())

        playback.apply(ReceiverCommand.Play)
        playback.apply(ReceiverCommand.Pause)

        assertTrue(backend.playCount >= 1, "play never reached the backend")
        assertTrue(backend.pauseCount >= 1, "pause never reached the backend")
    }

    @Test
    fun seekConvertsMillisecondsToTheSecondsTheBackendExpects() = runTest {
        val backend = RecordingReceiverBackend()
        val player = ComposedPlayer(backend = backend, scope = backgroundScope)
        player.setup(PlayerConfig())
        player.queue(listOf(Clip("a", "https://example.test/a.mkv")))
        val playback = ComposedPlayerReceiverPlayback(player, eager())

        playback.apply(ReceiverCommand.Seek(30_000))

        assertEquals(listOf(30.0), backend.seekedTo)
    }
}
