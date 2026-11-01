// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.preferences

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tv.nomercy.player.core.controllers.InMemoryStorage
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.testing.FakeVideoBackend
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.item.VideoPlaylistItem
import tv.nomercy.player.video.item.WatchProgress
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The real player stack tells the overlay about a styled sidecar again on the
 * next item.
 *
 * The libass plugin forgets its selection on `item` and draws only what a
 * `subtitle` event names. A sidecar never survives an item change in the
 * player (the cue tracker is disposed and the engine is told null), so the
 * preferences plugin finds nothing in effect and announces the saved choice.
 * This pins that, because it is the one thing standing between a carried
 * selection and dark captions on the second episode.
 */
class StyledCaptionsAreAnnouncedOnTheNextItemTest {

    @Test
    fun theSavedStyledSidecarIsAnnouncedAgainOnceTheNextItemIsReady() = runTest {
        val backend = FakeVideoBackend()
        val player = NMVideoPlayer(backend = backend, video = backend, storage = InMemoryStorage())
        player.setup(PlayerConfig())
        player.queue(listOf(episode("a"), episode("b")))
        val prefs = VideoPreferencesPlugin(player, VideoPreferencesOptions())
        player.addPlugin(prefs)

        player.item("a")
        val full: SubtitleTrack = player.subtitles().first { it.format == "ass" }
        player.subtitle(full)
        prefs.awaitWrites()

        val announced = CompletableDeferred<Double?>()
        player.on(CoreEvents.Subtitle) { announced.complete(it.track) }
        player.item("b")
        player.emit(CoreEvents.MediaReady, Unit)

        val index: Double? = withContext(Dispatchers.Default) { withTimeout(TIMEOUT_MS) { announced.await() } }

        assertEquals(
            player.subtitles().indexOfFirst { it.format == "ass" }.toDouble(),
            index,
            "the styled track was not announced again on the next item",
        )
    }

    private fun episode(id: String): VideoPlaylistItem = object : VideoPlaylistItem {
        override val id: String = id
        override val url: String = "https://media.example.test/$id.m3u8"
        override val title: String? = null
        override val durationSeconds: Double? = null
        override val progress: WatchProgress? = null
        override val subtitles: List<SubtitleTrack> = listOf(
            SubtitleTrack(
                id = "$id-vtt", language = "eng", label = "English", format = "vtt",
                url = "https://media.example.test/$id/en.vtt",
            ),
            SubtitleTrack(
                id = "$id-ass", language = "eng", label = "English (Full)", format = "ass",
                url = "https://media.example.test/$id/full.ass",
            ),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
