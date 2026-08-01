// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitleCueChange
import tv.nomercy.player.core.events.TimeUpdate
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.testing.FakeFetcher
import tv.nomercy.player.testing.FakeVideoBackend
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.item.VideoPlaylistItem
import tv.nomercy.player.video.item.WatchProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// A .vtt beside a film, on screen.
//
// The whole sidecar half of a NoMercy library was unreachable: addSubtitleTrack
// appended to a list, SubtitleTrack carried no url, and selecting one asked the
// engine for a track it had never reported. Every one of those looks
// implemented from the outside, and together they mean a directory of subtitle
// files that nothing can play.
class SidecarSubtitleCuesTest {

    private class Film(
        override val id: String = "sintel",
        override val url: String = "https://films.test/sintel.m3u8",
        override val title: String? = "Sintel",
        override val durationSeconds: Double? = 888.0,
        override val progress: WatchProgress? = null,
        override val subtitles: List<SubtitleTrack> = emptyList(),
    ) : VideoPlaylistItem

    // On the test's own scope, so the fetch the selection starts is driven by
    // the test scheduler rather than by whichever thread wins. A player built
    // with its own scope makes every assertion here a race.
    private fun TestScope.playerWith(fetcher: FakeFetcher, backend: FakeVideoBackend = FakeVideoBackend()) =
        NMVideoPlayer(backend, backend, scope = backgroundScope, fetcher = fetcher)

    // The file is fetched on a coroutine, so a selection is not finished when
    // the call returns. Every test here has to let it land before it looks.
    private suspend fun NMVideoPlayer.choose(track: SubtitleTrack?) {
        subtitle(track)
        yield()
    }

    private fun cuesSeenBy(player: NMVideoPlayer): MutableList<SubtitleCueChange> {
        val seen: MutableList<SubtitleCueChange> = mutableListOf()
        player.on(CoreEvents.SubtitleCue) { seen += it }
        return seen
    }

    private fun tick(player: NMVideoPlayer, seconds: Double) {
        player.emit(CoreEvents.Time, TimeUpdate(time = seconds, duration = 888.0, percentage = 0.0))
    }

    @Test
    fun selectingASidecarPutsItsCuesOnTheChannel() = runTest {
        val fetcher = FakeFetcher().respondWith(body = VTT)
        val player = playerWith(fetcher)
        player.setup()
        val seen = cuesSeenBy(player)

        player.choose(DUTCH)
        tick(player, 1.5)

        assertEquals(listOf(FIRST_LINE), seen.last().cues.map { it.text })
        assertEquals("nl", seen.last().language)
    }

    // The file is read through the player's own transport, so a real install's
    // bearer token rides along with it. A producer that opened its own client
    // would be a second HTTP stack with its own idea of auth.
    @Test
    fun theFileIsFetchedThroughThePlayersTransport() = runTest {
        val fetcher = FakeFetcher().respondWith(body = VTT)
        val player = playerWith(fetcher)
        player.setup()

        player.choose(DUTCH)

        assertEquals(listOf(DUTCH.url), fetcher.calls.map { it.url })
    }

    // The positioning the file states, all the way to the event. The parser has
    // read `align` and `line` since it was written and nothing carried them.
    @Test
    fun theCuesPositioningSurvivesTheParse() = runTest {
        val fetcher = FakeFetcher().respondWith(body = VTT)
        val player = playerWith(fetcher)
        player.setup()
        val seen = cuesSeenBy(player)

        player.choose(DUTCH)
        tick(player, 5.5)

        val cue = seen.last().cues.single()
        assertEquals(SIGN, cue.text)
        assertEquals(10.0, cue.line)
        assertEquals("end", cue.align)
        assertEquals(60.0, cue.size)
    }

    @Test
    fun leavingACueClearsThePicture() = runTest {
        val fetcher = FakeFetcher().respondWith(body = VTT)
        val player = playerWith(fetcher)
        player.setup()
        val seen = cuesSeenBy(player)

        player.choose(DUTCH)
        tick(player, 1.5)
        tick(player, 3.5)

        assertTrue(seen.last().cues.isEmpty())
    }

    // Core answers "which subtitle is playing" off the engine, and the engine
    // has never heard of this track. Without an answer here the menu shows no
    // tick beside the file the viewer just chose.
    @Test
    fun theSelectedSidecarIsWhatThePlayerReportsBack() = runTest {
        val fetcher = FakeFetcher().respondWith(body = VTT)
        val player = playerWith(fetcher)
        player.setup()

        player.choose(DUTCH)

        assertEquals(DUTCH.id, player.subtitle()?.id)
    }

    @Test
    fun turningSubtitlesOffStopsTheFile() = runTest {
        val fetcher = FakeFetcher().respondWith(body = VTT)
        val player = playerWith(fetcher)
        player.setup()
        val seen = cuesSeenBy(player)

        player.choose(DUTCH)
        tick(player, 1.5)
        player.choose(null)
        tick(player, 1.6)

        assertNull(player.subtitle())
        assertTrue(seen.last().cues.isEmpty())
    }

    // The item names its own files, so a host queueing a film does not have to
    // register nine languages by hand — which is what "sidecars are the norm"
    // means in practice.
    @Test
    fun theItemsOwnFilesBecomeTracks() = runTest {
        val fetcher = FakeFetcher()
        val player = playerWith(fetcher)
        player.setup()

        player.queue(listOf<PlaylistItem>(Film(subtitles = listOf(DUTCH, ENGLISH))))

        assertEquals(listOf(DUTCH.id, ENGLISH.id), player.subtitles().map { it.id })
    }

    // A sidecar covering a language the container also carries wins. The viewer
    // chooses a language once, and the file somebody put beside the film is the
    // one they meant.
    @Test
    fun aSidecarDisplacesTheEnginesTrackInTheSameLanguage() = runTest {
        val backend = FakeVideoBackend()
        backend.subtitleTracks = listOf(SubtitleTrack(id = "text:0", language = "nl", label = "Dutch"))
        val player = playerWith(FakeFetcher(), backend)
        player.setup()

        player.queue(listOf<PlaylistItem>(Film(subtitles = listOf(DUTCH))))

        assertEquals(listOf(DUTCH.id), player.subtitles().map { it.id })
    }

    // The same language, spelled the two ways ISO 639-2 allows. A muxer writes
    // the bibliographic "dut" and the file beside it is named "nl", and comparing
    // the raw strings makes them two languages: the menu grows a Dutch row and a
    // second Dutch row, and the sidecar never displaces what it duplicates.
    @Test
    fun aSidecarDisplacesTheEnginesTrackSpelledInTheOtherIsoForm() = runTest {
        val backend = FakeVideoBackend()
        backend.subtitleTracks = listOf(SubtitleTrack(id = "text:0", language = "dut", label = "Nederlands"))
        val player = playerWith(FakeFetcher(), backend)
        player.setup()

        player.queue(listOf<PlaylistItem>(Film(subtitles = listOf(DUTCH))))

        assertEquals(listOf(DUTCH.id), player.subtitles().map { it.id })
    }

    // A regional sidecar displaces the plain track, because the key keeps only
    // the primary subtag: a viewer who wanted English is not offered English
    // twice because one of the two files happened to say en-GB.
    //
    // Asserted because it is a judgement call that could plausibly have gone the
    // other way, and the reference already made it — normalizeLanguage splits on
    // the hyphen before it looks anything up.
    @Test
    fun aRegionalSidecarDisplacesThePlainLanguageTrack() = runTest {
        val backend = FakeVideoBackend()
        backend.subtitleTracks = listOf(SubtitleTrack(id = "text:0", language = "en", label = "English"))
        val player = playerWith(FakeFetcher(), backend)
        player.setup()

        player.queue(listOf<PlaylistItem>(Film(subtitles = listOf(BRITISH))))

        assertEquals(listOf(BRITISH.id), player.subtitles().map { it.id })
    }

    private companion object {
        const val FIRST_LINE = "Wat is er met je hand gebeurd?"
        const val SIGN = "— SINTEL —"

        val DUTCH = SubtitleTrack(
            id = "sub-nl",
            language = "nl",
            label = "Nederlands",
            url = "https://films.test/sintel.dut.vtt",
        )

        val ENGLISH = SubtitleTrack(
            id = "sub-en",
            language = "en",
            label = "English",
            url = "https://films.test/sintel.eng.vtt",
        )

        val BRITISH = SubtitleTrack(
            id = "sub-en-gb",
            language = "en-GB",
            label = "English (UK)",
            url = "https://films.test/sintel.en-GB.vtt",
        )

        val VTT = """
            WEBVTT

            1
            00:00:01.000 --> 00:00:03.000
            $FIRST_LINE

            2
            00:00:05.000 --> 00:00:08.000 align:end line:10% size:60%
            $SIGN
        """.trimIndent()
    }
}
