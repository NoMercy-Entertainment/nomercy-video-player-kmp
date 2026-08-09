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
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.MediaBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class Clip(
    override val id: String,
    override val url: String = "https://example.test/$id",
    override val title: String? = null,
) : PlaylistItem

private class RecordingBackend : MediaBackend {
    val loaded: MutableList<String> = mutableListOf()
    var playCount: Int = 0
    var seekedTo: MutableList<Double> = mutableListOf()
    private var time: Double = 0.0
    private val listeners = mutableMapOf<String, MutableList<(Any?) -> Unit>>()

    override suspend fun load(url: String, opts: LoadOptions) { loaded += url }
    override suspend fun play() { playCount += 1 }
    override fun pause() = Unit
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

class NMVideoPlayerTest {

    private suspend fun player(): Pair<NMVideoPlayer, RecordingBackend> {
        val backend = RecordingBackend()
        val subject = NMVideoPlayer(backend)
        subject.setup()
        subject.ready().await()
        return subject to backend
    }

    @Test
    fun itIsACorePlayerBeforeItIsAVideoOne() = runTest {
        val (subject, backend) = player()
        subject.queue(listOf(Clip("a")))

        subject.play()

        // Transport, queue and state all come from the core composition. If this
        // ever needs restating in the video library, the split has failed.
        assertEquals(PlayState.PLAYING, subject.state().playState)
        assertEquals("a", subject.item()?.id)
        assertEquals(1, backend.playCount)
    }

    @Test
    fun aVideoEventAndACoreEventArriveOnTheSameBus() = runTest {
        val (subject, _) = player()
        val seen = mutableListOf<String>()
        subject.on(VideoEvents.Fullscreen) { seen += "fullscreen" }
        subject.on(CoreEvents.Play) { seen += "play" }
        subject.queue(listOf(Clip("a")))

        subject.fullscreen(true)
        subject.play()

        assertEquals(listOf("fullscreen", "play"), seen)
    }

    @Test
    fun aViewModeIsRecordedAndAnnouncedButNotActedOn() = runTest {
        val (subject, _) = player()
        var announced: Boolean? = null
        subject.on(VideoEvents.Fullscreen) { announced = it.active }

        subject.fullscreen(true)

        // The player cannot put itself fullscreen — that belongs to the window
        // or the Activity — so it says so and the chrome does it.
        assertTrue(subject.fullscreen())
        assertEquals(true, announced)
    }

    @Test
    fun settingAViewModeItIsAlreadyInAnnouncesNothing() = runTest {
        val (subject, _) = player()
        var announcements = 0
        subject.on(VideoEvents.Pip) { announcements += 1 }

        subject.pip(true)
        subject.pip(true)

        // A chrome that redraws on the event would otherwise flicker every time
        // a shortcut repeated.
        assertEquals(1, announcements)
    }

    @Test
    fun fullscreenAndTheaterCannotBothBeOn() = runTest {
        val (subject, _) = player()

        subject.theater(true)
        subject.fullscreen(true)

        assertTrue(subject.fullscreen())
        assertFalse(subject.theater())
    }

    @Test
    fun theTogglesInvertWhateverTheStateIs() = runTest {
        val (subject, _) = player()

        subject.toggleFullscreen()
        assertTrue(subject.fullscreen())
        subject.toggleFullscreen()
        assertFalse(subject.fullscreen())

        subject.togglePip()
        assertTrue(subject.pip())
    }

    @Test
    fun cyclingAspectRatioWalksTheModesAndComesBack() = runTest {
        val (subject, _) = player()
        val seen = mutableListOf<Stretching>()
        subject.on(VideoEvents.AspectRatio) { seen += it.value }

        repeat(Stretching.entries.size) { subject.cycleAspectRatio() }

        assertEquals(Stretching.entries.size, seen.size)
        assertEquals(Stretching.Uniform, subject.aspectRatio())
    }

    @Test
    fun theVideoRectIsNullUntilSomethingHasBeenDrawn() = runTest {
        val (subject, _) = player()
        var reported: VideoRect? = null
        subject.on(VideoEvents.VideoRect) { reported = it.rect }

        assertNull(subject.videoRect())

        subject.videoRect(VideoRect(x = 0.0, y = 40.0, width = 1920.0, height = 800.0))

        assertEquals(1920.0, subject.videoRect()?.width)
        assertEquals(40.0, reported?.y)
    }

    @Test
    fun playingASegmentSeeksToItsStart() = runTest {
        // It used to seek to the end, which is a skip rather than a segment. A
        // segment is a window with a boundary to announce, and it starts where
        // the region starts.
        val (subject, backend) = player()
        subject.queue(listOf(Clip("a")))
        subject.play()

        subject.playSegment(SegmentBoundary(id = "intro", kind = "intro", startTime = 12.0, endTime = 92.0))

        assertEquals(listOf(12.0), backend.seekedTo)
    }

    @Test
    fun aRefusedSeekRefusesTheSkipToo() = runTest {
        val (subject, backend) = player()
        subject.queue(listOf(Clip("a")))
        subject.play()
        subject.on(CoreEvents.BeforeSeek) { it.preventDefault() }

        subject.playSegment(SegmentBoundary(id = "intro", kind = "intro", startTime = 0.0, endTime = 92.0))

        // Skipping is a seek, so a plugin that refuses seeks refuses skips.
        assertTrue(backend.seekedTo.isEmpty())
    }

    @Test
    fun aMessageIsAnnouncedAndCanBeCleared() = runTest {
        val (subject, _) = player()
        val seen = mutableListOf<String>()
        subject.on(VideoEvents.DisplayMessage) { seen += it.text }
        var cleared = 0
        subject.on(VideoEvents.RemoveMessage) { cleared += 1 }

        subject.message("Skipped intro", ms = 2_000.0)
        subject.clearMessage()

        assertEquals(listOf("Skipped intro"), seen)
        assertEquals(1, cleared)
    }

    @Test
    fun everyPlayerBuiltIsFindableWithoutThreadingAReference() = runTest {
        val before = NMVideoPlayer.instances().size

        val (subject, _) = player()

        assertEquals(before + 1, NMVideoPlayer.instances().size)
        assertTrue(NMVideoPlayer.instances().contains(subject))
    }
}
