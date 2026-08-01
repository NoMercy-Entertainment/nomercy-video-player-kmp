// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import tv.nomercy.player.testing.FakeVideoBackend

// A foreign-language film opened with subtitles off, because
// defaultSubtitleLanguage and defaultAudioLanguage did not exist and nothing
// matched a language tag anywhere. The fallback order is the part that gets
// guessed wrong, so it is enumerated here rather than sampled.
class DefaultTracksAndSegmentsTest {

    private fun subtitles(vararg tags: String): List<SubtitleTrack> =
        tags.map { SubtitleTrack(id = it, language = it, label = it) }

    private fun audio(vararg tags: String): List<AudioTrack> =
        tags.map { AudioTrack(id = it, language = it, label = it) }

    private fun TestScope.player(
        config: PlayerConfig,
        subtitleTracks: List<SubtitleTrack> = emptyList(),
        audioTracks: List<AudioTrack> = emptyList(),
    ): NMVideoPlayer {
        val backend = FakeVideoBackend()
        backend.subtitleTracks = subtitleTracks
        backend.audio = audioTracks
        val subject = NMVideoPlayer(backend, backend, backgroundScope)
        backgroundScope.launch { subject.setup(config) }
        runCurrent()
        subject.emit(CoreEvents.MediaReady, Unit)
        return subject
    }

    @Test
    fun anExactTagWins() = runTest {
        val subject = player(PlayerConfig(defaultSubtitleLanguage = "nl"), subtitles("en", "nl", "de"))

        assertEquals("nl", subject.subtitle()?.language)
    }

    @Test
    fun aPrefixMatchIsTakenWhenNoTagIsExact() = runTest {
        val subject = player(PlayerConfig(defaultSubtitleLanguage = "en"), subtitles("nl", "en-US"))

        assertEquals("en-US", subject.subtitle()?.language)
    }

    @Test
    fun theMatchAlsoRunsTheOtherDirection() = runTest {
        val subject = player(PlayerConfig(defaultSubtitleLanguage = "en-GB"), subtitles("de", "en"))

        assertEquals("en", subject.subtitle()?.language)
    }

    @Test
    fun anExactMatchLaterInTheListStillBeatsAnEarlierPrefixMatch() = runTest {
        // The fallback order, stated. A first-match-wins loop would pick en-US.
        val subject = player(PlayerConfig(defaultSubtitleLanguage = "en"), subtitles("en-US", "en"))

        assertEquals("en", subject.subtitle()?.language)
    }

    @Test
    fun theTagIsMatchedWithoutRegardToCase() = runTest {
        val subject = player(PlayerConfig(defaultSubtitleLanguage = "EN"), subtitles("de", "en"))

        assertEquals("en", subject.subtitle()?.language)
    }

    @Test
    fun noMatchLeavesTheSelectionAlone() = runTest {
        // A viewer who asked for French on a film that has none is not owed an
        // error, and turning captions off to signal the miss would make the
        // absence worse than it is.
        val subject = player(PlayerConfig(defaultSubtitleLanguage = "fr"), subtitles("en", "nl"))

        assertNull(subject.subtitle())
    }

    @Test
    fun theAudioTrackIsChosenTheSameWay() = runTest {
        val subject = player(PlayerConfig(defaultAudioLanguage = "en"), audioTracks = audio("nl", "en-US"))

        assertEquals("en-US", subject.audioTrack()?.language)
    }

    @Test
    fun aHostWithNoOpinionGetsTheEnginesOwnPick() = runTest {
        val subject = player(PlayerConfig(), subtitles("en", "nl"))

        assertNull(subject.subtitle())
    }

    // ── Segment windows ──────────────────────────────────────────────────────

    private fun TestScope.playing(): Pair<NMVideoPlayer, FakeVideoBackend> {
        val backend = FakeVideoBackend()
        val subject = NMVideoPlayer(backend, backend, backgroundScope)
        backgroundScope.launch {
            subject.setup(PlayerConfig())
            subject.play()
        }
        runCurrent()
        return subject to backend
    }

    @Test
    fun aLoopingWindowSeeksBackRatherThanRunningOn() = runTest {
        val (subject, backend) = playing()
        subject.playSegment(SegmentBoundary("intro", "intro", 10.0, 20.0), SegmentEndBehaviour.Loop)
        runCurrent()

        backend.tick(21.0)
        runCurrent()

        assertEquals(10.0, subject.time(), "onEnd was ignored, so every window behaved as Advance")
    }

    @Test
    fun aLoopingWindowStaysOpen() = runTest {
        val (subject, backend) = playing()
        subject.playSegment(SegmentBoundary("intro", "intro", 10.0, 20.0), SegmentEndBehaviour.Loop)
        runCurrent()
        var boundaries = 0
        subject.on(VideoEvents.SegmentBoundary) { boundaries += 1 }

        backend.tick(21.0)
        runCurrent()
        backend.tick(21.0)
        runCurrent()

        assertEquals(2, boundaries)
    }

    @Test
    fun aHoldingWindowPausesOnTheLastFrame() = runTest {
        val (subject, backend) = playing()
        subject.playSegment(SegmentBoundary("intro", "intro", 10.0, 20.0), SegmentEndBehaviour.Hold)
        runCurrent()

        backend.tick(21.0)
        runCurrent()

        assertEquals(PlayState.PAUSED, subject.playState())
    }

    @Test
    fun advanceSaysSoAndLetsPlaybackCarryOn() = runTest {
        val (subject, backend) = playing()
        subject.playSegment(SegmentBoundary("credits", "credits", 10.0, 20.0), SegmentEndBehaviour.Advance)
        runCurrent()
        var boundaries = 0
        subject.on(VideoEvents.SegmentBoundary) { boundaries += 1 }

        backend.tick(21.0)
        runCurrent()
        backend.tick(22.0)
        runCurrent()

        assertEquals(PlayState.PLAYING, subject.playState())
        assertEquals(1, boundaries, "a closed window must not keep firing")
    }
}
