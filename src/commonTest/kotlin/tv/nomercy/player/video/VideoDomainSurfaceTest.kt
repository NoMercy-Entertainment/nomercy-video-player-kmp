// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.core.player.AudioTrackState
import tv.nomercy.player.core.ports.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tv.nomercy.player.testing.FakeVideoBackend

private val DUTCH = SubtitleTrack(id = "sub-nl", language = "nl", label = "Nederlands")
private val ENGLISH = SubtitleTrack(id = "sub-en", language = "en", label = "English")

// The video domain the contract names and this library owes.
class VideoDomainSurfaceTest {

    private suspend fun player(): Pair<NMVideoPlayer, FakeVideoBackend> {
        val backend = FakeVideoBackend()
        val subject = NMVideoPlayer(backend)
        subject.setup()
        subject.ready().await()
        subject.queue(listOf(VideoItem("ep-1")))
        return subject to backend
    }

    @Test
    fun aSegmentAnnouncesWhenThePlayheadLeavesIt() = runTest {
        // The window is the point: a "skip intro" button appears while the intro
        // is playing and disappears when it ends, and only something watching
        // the playhead knows when that is.
        val (subject, backend) = player()
        val reached: MutableList<String> = mutableListOf()
        subject.on(VideoEvents.SegmentBoundary) { reached += it.id }

        subject.playSegment(SegmentBoundary("intro", "intro", startTime = 30.0, endTime = 90.0))
        backend.tick(60.0)
        assertEquals(emptyList(), reached, "the boundary fired while the playhead was still inside it")

        backend.tick(91.0)

        assertEquals(listOf("intro"), reached)
    }

    @Test
    fun aSegmentSeeksToItsStartRatherThanJumpingTheQueue() = runTest {
        val (subject, backend) = player()

        subject.playSegment(SegmentBoundary("recap", "recap", startTime = 12.0, endTime = 40.0))

        assertEquals(listOf(12.0), backend.seekedTo)
    }

    @Test
    fun clearingASegmentStopsTheWatcher() = runTest {
        // A watcher left running across a track change fires on a timestamp that
        // means something else now.
        val (subject, backend) = player()
        val reached: MutableList<String> = mutableListOf()
        subject.on(VideoEvents.SegmentBoundary) { reached += it.id }
        subject.playSegment(SegmentBoundary("intro", "intro", startTime = 0.0, endTime = 90.0))

        subject.clearSegment()
        backend.tick(120.0)

        assertEquals(emptyList(), reached)
    }

    @Test
    fun startingASecondSegmentReplacesTheFirst() = runTest {
        // Two watchers on one playhead both fire, and the second one's boundary
        // is about a region the viewer has already left.
        val (subject, backend) = player()
        val reached: MutableList<String> = mutableListOf()
        subject.on(VideoEvents.SegmentBoundary) { reached += it.id }

        subject.playSegment(SegmentBoundary("intro", "intro", startTime = 0.0, endTime = 90.0))
        subject.playSegment(SegmentBoundary("credits", "credits", startTime = 100.0, endTime = 200.0))
        backend.tick(201.0)

        assertEquals(listOf("credits"), reached)
    }

    @Test
    fun aSubtitleStartsOutTheEnginesChoiceNotTheViewers() = runTest {
        val (subject, _) = player()

        assertEquals(AudioTrackState.DEFAULT, subject.subtitleState())
    }

    @Test
    fun theSubtitleStyleIsHeldOnceAndAnnouncedToWhateverDraws() = runTest {
        // Core has no renderer — libass draws these on three platforms and a
        // Compose overlay on none of them — so this is the one place the
        // viewer's preference lives and every renderer reads the same answer.
        val (subject, _) = player()
        val styles: MutableList<SubtitleStyle> = mutableListOf()
        subject.on(CoreEvents.SubtitleStyle) { styles += it }

        val chosen = SubtitleStyle(fontSize = 28.0, color = "#FFEE00")
        subject.subtitleStyle(chosen)

        assertEquals(chosen, subject.subtitleStyle())
        assertEquals(listOf(chosen), styles)
    }

    @Test
    fun anExternalSubtitleJoinsWhatTheEngineReported() = runTest {
        // A sidecar the viewer downloaded, or one the server found after the
        // item was already playing.
        val (subject, backend) = player()
        backend.subtitleTracks = listOf(ENGLISH)

        subject.addSubtitleTrack(DUTCH)

        assertEquals(listOf("sub-en", "sub-nl"), subject.subtitles().map { it.id })
    }

    @Test
    fun addingTheSameTrackTwiceDoesNotDuplicateIt() = runTest {
        // A host re-adding on every resume is ordinary, and a menu with the same
        // language twice is the visible symptom.
        val (subject, _) = player()

        subject.addSubtitleTrack(DUTCH)
        subject.addSubtitleTrack(DUTCH)

        assertEquals(1, subject.subtitles().count { it.id == "sub-nl" })
    }

    @Test
    fun removingTheShowingTrackTurnsSubtitlesOff() = runTest {
        // Otherwise the renderer keeps drawing cues from a file nobody can
        // select any more.
        val (subject, _) = player()
        subject.addSubtitleTrack(DUTCH)
        subject.subtitle(DUTCH)

        subject.removeSubtitleTrack("sub-nl")

        assertNull(subject.subtitle())
        assertTrue(subject.subtitles().none { it.id == "sub-nl" })
    }

    @Test
    fun removingSomethingThatWasNeverThereChangesNothing() = runTest {
        val (subject, _) = player()
        subject.addSubtitleTrack(DUTCH)

        subject.removeSubtitleTrack("never-added")

        assertEquals(listOf("sub-nl"), subject.subtitles().map { it.id })
    }

    @Test
    fun cyclingSubtitlesReachesOffAndComesBack() = runTest {
        // Off is part of the cycle because that is what a remote's subtitle
        // button does. A cycle that never reached off would trap a viewer who
        // turned them on by accident.
        val (subject, backend) = player()
        backend.subtitleTracks = listOf(ENGLISH, DUTCH)

        subject.cycleSubtitles()
        assertEquals("sub-en", subject.subtitle()?.id)

        subject.cycleSubtitles()
        assertEquals("sub-nl", subject.subtitle()?.id)

        subject.cycleSubtitles()
        assertNull(subject.subtitle(), "the cycle never reached off")

        subject.cycleSubtitles()
        assertEquals("sub-en", subject.subtitle()?.id)
    }

    @Test
    fun cyclingSubtitlesMakesTheChoiceTheViewers() = runTest {
        val (subject, backend) = player()
        backend.subtitleTracks = listOf(ENGLISH)

        subject.cycleSubtitles()

        assertEquals(AudioTrackState.MANUAL, subject.subtitleState())
    }

    @Test
    fun cyclingAudioNeverProducesSilence() = runTest {
        // An item with no audio is not a state a viewer can ask for, and a cycle
        // that produced it would look like a broken track rather than a choice.
        val (subject, backend) = player()
        backend.audio = listOf(
            tv.nomercy.player.core.ports.AudioTrack("a-en", "en", "English"),
            tv.nomercy.player.core.ports.AudioTrack("a-ja", "ja", "日本語"),
        )

        repeat(3) { subject.cycleAudioTracks() }

        assertTrue(subject.audioTrack() != null, "cycling audio reached a state with no track")
    }

    @Test
    fun cyclingASingleTrackIsANoOp() = runTest {
        // One track and one press should not toggle it off and leave a viewer
        // wondering where the audio went.
        val (subject, backend) = player()
        backend.audio = listOf(tv.nomercy.player.core.ports.AudioTrack("a-en", "en", "English"))
        val before = subject.audioTrack()

        subject.cycleAudioTracks()

        assertEquals(before, subject.audioTrack())
    }

    @Test
    fun normalisingAnItemIsIdentityUntilALibrarySaysOtherwise() = runTest {
        // Core cannot know a wire format it was never told about, and a
        // normaliser that guessed would corrupt the fields it guessed wrong.
        val (subject, _) = player()
        val item = VideoItem("ep-2")

        assertEquals(item, subject.normalizePlaylistItem(item))
    }
}
