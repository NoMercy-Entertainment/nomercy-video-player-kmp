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
import tv.nomercy.player.core.ports.CanonicalBackendEvent
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

    private fun playing(name: String, body: (NMVideoPlayer, ExoPlayerVideoBackend) -> Unit) {
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
            body(player, backend)
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
        playing("e2e-tracks.mp4") { player, _ ->
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
        playing("e2e-cycle-audio.mp4") { player, _ ->
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
        playing("e2e-cycle-wrap.mp4") { player, _ ->
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
        playing("e2e-subs.mp4") { player, _ ->
            val fromEngine: Int = player.subtitles().size
            val sidecar = SubtitleTrack(id = "sidecar-nl", language = "nl", label = "Nederlands", format = "ass")

            player.addSubtitleTrack(sidecar)

            assertEquals(fromEngine + 1, player.subtitles().size, "the sidecar track did not join the list")
            assertTrue(player.subtitles().any { it.id == "sidecar-nl" })

            runBlocking { player.removeSubtitleTrack("sidecar-nl") }
            assertEquals(fromEngine, player.subtitles().size, "removing the sidecar left it behind")
        }
    }

    @Test
    fun theEngineAnnouncesTheCanonicalSpineToTheLibrarysConsumers() {
        // The event vocabulary is the contract every chrome in the ecosystem is
        // written against, and this is the only place it is observed arriving
        // through the video library rather than straight off a backend. A
        // consumer subscribes here, not to Media3.
        //
        // The recorder is local because core's lives in its test source set,
        // which is not published — a downstream consumer could not use it
        // either, so a gate that borrowed it would be testing a path nobody has.
        val seen: MutableList<String> = mutableListOf()

        playing("e2e-spine.mp4") { player, backend ->
            SPINE.forEach { name -> backend.on(name) { synchronized(seen) { seen += name } } }

            runBlocking { player.play() }
            Thread.sleep(PLAY_MS)
            runBlocking { player.pause() }
            Thread.sleep(SETTLE_MS)
        }

        assertCanonicalSubsequence(synchronized(seen) { seen.toList() }, SPINE)
    }

    // A subsequence, not an equality. The engine emits more than these — the
    // timeupdate alone repeats several times a second — and pinning an exact
    // list would make every future event a breaking change. What must hold is
    // the order of the ones a consumer builds behaviour on.
    //
    // Verified by reversing the required order and watching it redden, because
    // a subsequence check that always passes is indistinguishable from one that
    // works until the day it matters.
    private fun assertCanonicalSubsequence(recorded: List<String>, required: List<String>) {
        var index = 0
        for (name in recorded) {
            if (index < required.size && name == required[index]) index++
        }
        assertEquals(
            required.size,
            index,
            "canonical order not satisfied. required in this order: $required, recorded: $recorded",
        )
    }

    @Test
    fun chapterNavigationMovesRealPlaybackTimeThroughTheWholeStack() {
        // P20.8 — chapters proven against the real engine, not a fake clock.
        // The 8-second fixture gets three chapters at 0/3/6s; seekToChapter is
        // the library's own transport, so if this passes, a chapter menu built
        // on it moves the actual decoder rather than a number nobody is
        // watching.
        playing("e2e-chapters.mp4") { player, backend ->
            player.chapters(
                listOf(
                    tv.nomercy.player.core.media.Chapter(startTime = 0.0, title = "Open"),
                    tv.nomercy.player.core.media.Chapter(startTime = 3.0, title = "Middle"),
                    tv.nomercy.player.core.media.Chapter(startTime = 6.0, title = "Close"),
                ),
            )

            runBlocking { player.seekToChapter(2) }
            Thread.sleep(SETTLE_MS)
            assertTrue(
                backend.currentTime() >= 6.0 - SEEK_TOLERANCE_S,
                "seekToChapter(2) landed at ${backend.currentTime()}, expected close to 6.0",
            )
            assertEquals("Close", player.chapter()?.title, "the library's own chapter() disagrees with where seekToChapter put the engine")

            runBlocking { player.previousChapter() }
            Thread.sleep(SETTLE_MS)
            assertTrue(
                backend.currentTime() >= 3.0 - SEEK_TOLERANCE_S && backend.currentTime() < 6.0,
                "previousChapter from the third chapter landed at ${backend.currentTime()}, expected close to 3.0",
            )
        }
    }

    @Test
    fun theTransportSurvivesTheWholeStack() {
        // Play and pause through the library rather than the backend, so the
        // controllers between them are in the path. State is read back off the
        // engine because that is the one that actually has to have moved.
        playing("e2e-transport.mp4") { player, _ ->
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
private const val SEEK_TOLERANCE_S = 0.5

// The order a consumer builds behaviour on. Loadstart is not in it because it
// fires before this gate can subscribe — the load is what makes the engine
// exist to subscribe to.
private val SPINE: List<String> = listOf(
    CanonicalBackendEvent.PLAY,
    CanonicalBackendEvent.TIME_UPDATE,
    CanonicalBackendEvent.PAUSE,
)
