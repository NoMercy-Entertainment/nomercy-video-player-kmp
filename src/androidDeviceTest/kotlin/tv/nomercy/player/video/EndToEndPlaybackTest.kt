// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.BackendEvents
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.ExoPlayerVideoBackend
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.SubtitleTrack
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// The video library driving a real engine on real hardware.
//
// Everything else in this repo is measured against fakes, which is right for
// domain logic and proves nothing about the seam. The library is handed a
// backend it did not build, and the one thing a fake cannot check is whether it
// passes that backend on correctly — which is exactly the defect this repo has
// already had once: NMVideoPlayer took an engine and never gave it to core's
// video slot, so the whole track surface answered empty and every menu built
// from it was blank. Nothing failed. The menus were just empty.
//
// So this composes the real thing end to end: Media3 underneath, core's
// controllers in the middle, the video library's own API on top.
class EndToEndPlaybackTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun playing(name: String, body: (NMVideoPlayer) -> Unit) {
        val media: File = writeDualAudioClip(File(context.cacheDir, name))
        val backend = ExoPlayerVideoBackend(context)
        val player = NMVideoPlayer(backend, video = backend)
        val ready = CountDownLatch(1)

        try {
            // The lifecycle a host actually follows. Skipping it read tracks
            // fine and then refused transport with core:player/not-ready, which
            // is the library telling the truth — a player is not usable until it
            // has been set up, and a gate that drove it without setup was
            // testing a shape no consumer has.
            runBlocking { player.setup() }

            backend.on(BackendEvents.LOADED_METADATA) { ready.countDown() }
            runBlocking { backend.load(media.absolutePath, LoadOptions()) }
            assertTrue(
                ready.await(READY_TIMEOUT_S, TimeUnit.SECONDS),
                "the engine never reported metadata for a file it was handed",
            )
            body(player)
        } finally {
            backend.release()
            media.delete()
        }
    }

    @Test
    fun theLibraryReportsTheTracksTheEngineFound() {
        // The regression this file exists for. A player that did not pass its
        // engine to core's video slot answers empty here — and empty is what a
        // title with no alternate audio also looks like, which is why this went
        // unnoticed.
        playing("e2e-tracks.mp4") { player ->
            val audio: List<AudioTrack> = player.audioTracks()

            assertEquals(EXPECTED_DUBS, audio.size, "the library reports ${audio.size} dubs, the file has $EXPECTED_DUBS")
            assertTrue(audio.map { it.language }.containsAll(listOf("en", "nl")), "wrong languages: $audio")
            assertNotNull(player.audioTrack(), "no current dub while playing")
        }
    }

    @Test
    fun cyclingAudioMovesThroughTheListTheViewerSees() {
        // Through the displayed list, not the engine's own numbering. The two
        // are different orders and binding a control to the raw index is a
        // recurring defect here: the menu highlights one row and the sound
        // comes from another.
        playing("e2e-cycle-audio.mp4") { player ->
            val before: AudioTrack = assertNotNull(player.audioTrack())

            player.cycleAudioTracks()
            Thread.sleep(SETTLE_MS)
            val after: AudioTrack = assertNotNull(player.audioTrack())

            assertTrue(after.id != before.id, "cycling stayed on ${before.language}")
            assertTrue(
                after.id in player.audioTracks().map { it.id },
                "cycling landed on a track the menu does not contain",
            )
        }
    }

    @Test
    fun cyclingAudioComesBackAroundRatherThanRunningOut() {
        // Two tracks means the second cycle returns to the first. A cycle that
        // walked off the end would leave a viewer unable to get back to the
        // audio they started on without reopening the title.
        playing("e2e-cycle-wrap.mp4") { player ->
            val start: AudioTrack = assertNotNull(player.audioTrack())

            repeat(EXPECTED_DUBS) {
                player.cycleAudioTracks()
                Thread.sleep(SETTLE_MS)
            }

            assertEquals(start.id, player.audioTrack()?.id, "cycling did not return to where it began")
        }
    }

    @Test
    fun anExternalSubtitleJoinsTheEnginesOwnList() {
        // Sidecar subtitles are the common case for this library — the engine
        // knows nothing about them, and the merged list is what a menu draws.
        playing("e2e-subs.mp4") { player ->
            val fromEngine: Int = player.subtitles().size
            val sidecar = SubtitleTrack(id = "sidecar-nl", language = "nl", label = "Nederlands", format = "ass")

            player.addSubtitleTrack(sidecar)

            assertEquals(fromEngine + 1, player.subtitles().size, "the sidecar track did not join the list")
            assertTrue(player.subtitles().any { it.id == "sidecar-nl" })

            player.removeSubtitleTrack("sidecar-nl")
            assertEquals(fromEngine, player.subtitles().size, "removing the sidecar left it behind")
        }
    }

    @Test
    fun theTransportSurvivesTheWholeStack() {
        // Play and pause through the library rather than the backend, so the
        // controllers between them are in the path. State is read back off the
        // engine because that is the one that actually has to have moved.
        playing("e2e-transport.mp4") { player ->
            runBlocking { player.play() }
            Thread.sleep(PLAY_MS)
            val advanced: Boolean = player.time() > 0.0

            runBlocking { player.pause() }
            Thread.sleep(SETTLE_MS)

            assertTrue(advanced, "time never advanced through the library's own play(), stayed at ${player.time()}")
            assertEquals(
                BackendState.PAUSED,
                player.streamState(),
                "the engine is still ${player.streamState()} after the library's pause()",
            )
        }
    }
}

private const val READY_TIMEOUT_S = 20L
private const val SETTLE_MS = 1_200L
private const val PLAY_MS = 2_000L
private const val EXPECTED_DUBS = 2
