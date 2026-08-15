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
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.MediaBackend
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.core.ports.VideoBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

// A ladder the caller controls and a quality pick this test can read back —
// the minimum VideoBackend needs to prove SetQuality resolves against the
// right list, which RecordingReceiverBackend above (a plain MediaBackend)
// cannot do.
private class RecordingVideoBackend(private val levels: List<QualityLevel>) : VideoBackend {
    var qualityPicked: QualityLevel? = null
        private set
    private var time: Double = 0.0
    private val listeners = mutableMapOf<String, MutableList<(Any?) -> Unit>>()

    override suspend fun load(url: String, opts: LoadOptions) = Unit
    override suspend fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun release() = Unit
    override fun currentTime(): Double = time
    override fun currentTime(seconds: Double) { time = seconds }
    override fun duration(): Double = 0.0
    override fun volume(): Float = 1.0f
    override fun volume(value: Float) = Unit
    override fun mute() = Unit
    override fun unmute() = Unit
    override fun playbackRate(): Double = 1.0
    override fun playbackRate(rate: Double) = Unit
    override fun state(): BackendState = BackendState.IDLE
    override fun on(event: String, fn: (Any?) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(fn)
    }
    override fun off(event: String, fn: (Any?) -> Unit) { listeners[event]?.remove(fn) }

    override fun audioTracks(): List<AudioTrack> = emptyList()
    override fun audioTrack(): AudioTrack? = null
    override fun audioTrack(track: AudioTrack) = Unit
    override fun subtitleTracks(): List<SubtitleTrack> = emptyList()
    override fun subtitleTrack(): SubtitleTrack? = null
    override fun subtitleTrack(track: SubtitleTrack?) = Unit
    override fun qualityLevels(): List<QualityLevel> = levels
    override fun quality(): QualityLevel? = qualityPicked
    override fun quality(level: QualityLevel?) { qualityPicked = level }
}

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

    // The exact scenario the audit reproduced: an audio-only h=0 entry
    // (MpvEditionTitle.kt's real HLS editions) ahead of five real video
    // rungs. LOW/MEDIUM/HIGH pick a position within the height>0 subset, and
    // the bug was handing that position straight to qualityMode(index) —
    // an index into the UNFILTERED list, one entry longer.
    private fun ladder(): List<QualityLevel> = listOf(
        QualityLevel(height = 0, bitrate = 128_000, codec = "aac", label = "audio"),
        QualityLevel(height = 360, bitrate = 800_000, codec = "avc1"),
        QualityLevel(height = 480, bitrate = 1_500_000, codec = "avc1"),
        QualityLevel(height = 720, bitrate = 3_000_000, codec = "avc1"),
        QualityLevel(height = 1080, bitrate = 6_000_000, codec = "avc1"),
        QualityLevel(height = 2160, bitrate = 16_000_000, codec = "avc1"),
    )

    @Test
    fun setQualityHighPicksTheActualHighestRungNotThePreviousOne() = runTest {
        val backend = RecordingVideoBackend(ladder())
        val player = ComposedPlayer(backend = backend, video = backend, scope = backgroundScope)
        player.setup(PlayerConfig())
        val playback = ComposedPlayerReceiverPlayback(player, eager())

        playback.apply(ReceiverCommand.SetQuality(tv.nomercy.player.video.cast.RemoteQualityLevel.HIGH))

        assertEquals(2160, backend.qualityPicked?.height, "HIGH landed on the wrong rung against the unfiltered list")
    }

    @Test
    fun setQualityLowPicksTheLowestVideoRungNotTheAudioOnlyEntry() = runTest {
        val backend = RecordingVideoBackend(ladder())
        val player = ComposedPlayer(backend = backend, video = backend, scope = backgroundScope)
        player.setup(PlayerConfig())
        val playback = ComposedPlayerReceiverPlayback(player, eager())

        playback.apply(ReceiverCommand.SetQuality(tv.nomercy.player.video.cast.RemoteQualityLevel.LOW))

        assertEquals(360, backend.qualityPicked?.height)
    }

    @Test
    fun setQualityMediumPicksTheMiddleVideoRung() = runTest {
        val backend = RecordingVideoBackend(ladder())
        val player = ComposedPlayer(backend = backend, video = backend, scope = backgroundScope)
        player.setup(PlayerConfig())
        val playback = ComposedPlayerReceiverPlayback(player, eager())

        playback.apply(ReceiverCommand.SetQuality(tv.nomercy.player.video.cast.RemoteQualityLevel.MEDIUM))

        assertEquals(720, backend.qualityPicked?.height)
    }

    @Test
    fun setQualityAutoHandsTheRungChoiceBackToTheEngine() = runTest {
        val backend = RecordingVideoBackend(ladder())
        val player = ComposedPlayer(backend = backend, video = backend, scope = backgroundScope)
        player.setup(PlayerConfig())
        val playback = ComposedPlayerReceiverPlayback(player, eager())

        playback.apply(ReceiverCommand.SetQuality(tv.nomercy.player.video.cast.RemoteQualityLevel.AUTO))

        assertNull(backend.qualityPicked)
    }
}
