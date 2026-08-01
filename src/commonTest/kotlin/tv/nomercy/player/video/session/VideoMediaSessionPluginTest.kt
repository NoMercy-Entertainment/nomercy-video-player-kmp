// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.session

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.plugin.TransportCommands
import tv.nomercy.player.core.ports.NowPlaying
import tv.nomercy.player.core.ports.SystemTransport
import tv.nomercy.player.core.ports.TransportActions
import tv.nomercy.player.core.ports.TransportPlaybackState
import tv.nomercy.player.video.item.VideoPlaylistItem
import tv.nomercy.player.video.item.WatchProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private data class Episode(
    override val id: String = "e1",
    override val url: String = "https://media.example.test/dark-s02e03.mkv",
    override val title: String? = "Lost and Found",
    override val durationSeconds: Double? = null,
    override val progress: WatchProgress? = null,
    override val image: String? = null,
    override val poster: String? = null,
    override val thumbnail: String? = null,
    override val show: String? = null,
    override val season: Int? = null,
) : VideoPlaylistItem

private class CapturingTransport : SystemTransport {
    var lastNowPlaying: NowPlaying? = null
        private set

    override fun setNowPlaying(nowPlaying: NowPlaying) {
        lastNowPlaying = nowPlaying
    }

    override fun setPlaybackState(state: TransportPlaybackState, positionMs: Long, playbackRate: Double) = Unit
    override fun setActionHandlers(actions: TransportActions) = Unit
    override fun clear() = Unit
    override fun release() = Unit
}

private class SilentCommands : TransportCommands {
    override fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun seekTo(positionMs: Long) = Unit
}

// The three lines and the picture a television episode gets on a lock screen.
//
// Through the real plugin and the real event bus, because the mapping is the
// whole thing this class does: which field of an episode becomes which line of
// a Now Playing surface, and which of four image field names wins.
class VideoMediaSessionPluginTest {

    private suspend fun announce(item: PlaylistItem, locale: String = "en"): NowPlaying? {
        val transport = CapturingTransport()
        val player = ComposedPlayer(backend = null)
        player.setup(PlayerConfig())
        player.addPlugin(VideoMediaSessionPlugin(SilentCommands(), { transport }, locale))

        player.emit(CoreEvents.Item, ItemChange(item = item, index = 0))
        return transport.lastNowPlaying
    }

    @Test
    fun aSeriesEpisodeReachesTheLockScreenAsTitleShowAndSeason() = runTest {
        val playing: NowPlaying? = announce(
            Episode(show = "Dark", season = 2, image = "https://images.example.test/dark.jpg"),
        )

        assertEquals("Lost and Found", playing?.title)
        assertEquals("Dark", playing?.artist)
        assertEquals("Season 2", playing?.album)
        assertEquals("https://images.example.test/dark.jpg", playing?.artworkUrl)
    }

    @Test
    fun theSeasonLabelIsASentenceInTheViewersLanguage() = runTest {
        // "Season 2" is not a number with a word in front of it — Dutch puts the
        // same two parts in the same order with a different word, and other
        // locales do not. The table is generated from the web plugin's own i18n
        // folder so a native viewer reads the string a web viewer reads.
        assertEquals("Seizoen 2", announce(Episode(season = 2), locale = "nl")?.album)
    }

    @Test
    fun aFilmHasNoSeasonLineRatherThanABlankOne() = runTest {
        // These platforms draw an empty album as a blank row under the title.
        assertNull(announce(Episode(show = null, season = null))?.album)
    }

    @Test
    fun theCoverIsReadFromWhicheverOfTheThreeNamesTheHostUses() = runTest {
        // Three field names for one picture, in the order the web item documents.
        // A host whose backend calls it a poster and a host whose backend calls
        // it a thumbnail both reach the lock screen.
        assertEquals(
            "poster.jpg",
            announce(Episode(poster = "poster.jpg", thumbnail = "thumb.jpg"))?.artworkUrl,
        )
        assertEquals("thumb.jpg", announce(Episode(thumbnail = "thumb.jpg"))?.artworkUrl)
        assertEquals(
            "image.jpg",
            announce(Episode(image = "image.jpg", poster = "poster.jpg"))?.artworkUrl,
        )
    }

    @Test
    fun anItemThatIsNotAVideoItemKeepsTheCoreTitleRatherThanFailing() = runTest {
        // A host can queue a bare PlaylistItem. It gets core's answer, which is
        // the title or the file name, and none of the video lines.
        val playing: NowPlaying? = announce(BareItem())

        assertEquals("Blade Runner 2049", playing?.title)
        assertNull(playing?.artist)
    }

    private data class BareItem(
        override val id: String = "1",
        override val url: String = "https://media.example.test/x.mkv",
        override val title: String? = "Blade Runner 2049",
    ) : PlaylistItem
}
