// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.media.DynamicRange
import tv.nomercy.player.core.media.QualityDescriptor
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.CanonicalBackendEvent
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.MediaBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// An engine that can be told to report something, so the mapping is exercised
// rather than asserted about.
private class ScriptableBackend : MediaBackend {
    private val listeners = mutableMapOf<String, MutableList<(Any?) -> Unit>>()

    fun report(event: String, data: Any? = null) {
        listeners[event]?.toList()?.forEach { it(data) }
    }

    override suspend fun load(url: String, opts: LoadOptions) = Unit
    override suspend fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun currentTime(): Double = 0.0
    override fun currentTime(seconds: Double) = Unit
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

class VideoBackendBridgeTest {

    private suspend fun wired(): Pair<NMVideoPlayer, ScriptableBackend> {
        val backend = ScriptableBackend()
        val player = NMVideoPlayer(backend)
        player.setup()
        player.ready().await()
        return player to backend
    }

    @Test
    fun theEnginesCanPlayReachesTheConsumer() = runTest {
        val (player, backend) = wired()
        var seen = 0
        player.on(VideoEvents.CanPlay) { seen += 1 }

        backend.report(CanonicalBackendEvent.CAN_PLAY)

        // A chrome enabling its play button reacts to this rather than to
        // playing, because by then the viewer has already waited.
        assertEquals(1, seen)
    }

    @Test
    fun waitingAndStalledStayDifferentEventsAllTheWayThrough() = runTest {
        val (player, backend) = wired()
        val seen = mutableListOf<String>()
        player.on(VideoEvents.Waiting) { seen += "waiting" }
        player.on(VideoEvents.Stalled) { seen += "stalled" }

        backend.report(CanonicalBackendEvent.WAITING)
        backend.report(CanonicalBackendEvent.STALLED)

        // The pipeline being hungry and the network having stopped feeding it
        // are different things, and a chrome shows different things for them.
        assertEquals(listOf("waiting", "stalled"), seen)
    }

    @Test
    fun theLadderIsAnnouncedWithDescriptorsRatherThanIndexes() = runTest {
        val (player, _) = wired()
        var levels: List<QualityDescriptor> = emptyList()
        player.on(VideoEvents.Levels) { levels = it.levels }

        player.videoBridge.announceLevels(
            listOf(
                QualityDescriptor(1080, 6_000_000, DynamicRange.Sdr, "avc1"),
                QualityDescriptor(2160, 20_000_000, DynamicRange.Hdr10, "hevc"),
            ),
        )

        assertEquals(listOf("1080p", "2160p HDR10"), levels.map { it.label() })
    }

    @Test
    fun detachingStopsTheEngineReachingAPlayerThatMovedOn() = runTest {
        val (player, backend) = wired()
        var seen = 0
        player.on(VideoEvents.Stalled) { seen += 1 }

        backend.report(CanonicalBackendEvent.STALLED)
        player.videoBridge.detach(backend)
        backend.report(CanonicalBackendEvent.STALLED)

        // Swapping engines mid-session otherwise leaves the old one emitting
        // into a player that has moved on.
        assertEquals(1, seen)
    }

    @Test
    fun theBridgeOnlyClaimsEventsTheVideoMapHasNamesFor() = runTest {
        val (player, backend) = wired()
        val videoNames = VideoEvents.all.map { it.name }.toSet()
        val seen = mutableListOf<String>()
        player.context.emitter.onAll { name, payload -> if (name in videoNames) seen += name }

        backend.report(CanonicalBackendEvent.CAN_PLAY)
        backend.report(CanonicalBackendEvent.WAITING)
        backend.report(CanonicalBackendEvent.STALLED)
        backend.report(CanonicalBackendEvent.PLAYING)
        backend.report(CanonicalBackendEvent.ENDED)

        // playing and ended are core's; the video bridge must not restate them.
        assertTrue(seen.toSet() == setOf("canplay", "waiting", "stalled"))
    }
}
