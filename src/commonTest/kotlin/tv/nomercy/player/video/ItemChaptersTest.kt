// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import tv.nomercy.player.core.media.Chapter
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.testing.FakeFetcher
import tv.nomercy.player.testing.FakeVideoBackend
import tv.nomercy.player.video.item.VideoPlaylistItem
import tv.nomercy.player.video.item.WatchProgress

// Chapters arriving on the item, which is the route the reference uses and this
// port had no field for at all. A host had to call chapters(list) by hand for
// every item it queued; one that did not got a chapter bar with no segments and
// a next-chapter button that moved nothing.
class ItemChaptersTest {

    private data class Episode(
        override val id: String = "ep-1",
        override val url: String = "https://films.test/ep-1.m3u8",
        override val title: String? = "Episode 1",
        override val durationSeconds: Double? = 900.0,
        override val progress: WatchProgress? = null,
        override val subtitles: List<SubtitleTrack> = emptyList(),
        override val chapters: List<Chapter> = emptyList(),
        override val chapterFile: String? = null,
    ) : VideoPlaylistItem

    private val chapterVtt = """
        WEBVTT

        00:00:00.000 --> 00:01:30.000
        Cold open

        00:01:30.000 --> 00:05:00.000
        Titles
    """.trimIndent()

    private fun TestScope.playerWith(fetcher: FakeFetcher = FakeFetcher()): NMVideoPlayer {
        val backend = FakeVideoBackend()
        return NMVideoPlayer(backend, backend, scope = backgroundScope, fetcher = fetcher)
    }

    @Test
    fun anItemsOwnChaptersBecomeThePlayersWithoutTheHostAskingTwice() = runTest {
        val player = playerWith()
        player.setup()

        player.queue(
            listOf(
                Episode(
                    chapters = listOf(
                        Chapter(startTime = 0.0, title = "Cold open"),
                        Chapter(startTime = 90.0, title = "Titles"),
                    ),
                ),
            ),
        )
        yield()

        assertEquals(listOf("Cold open", "Titles"), player.chapters().map { it.title })
    }

    @Test
    fun aSidecarChapterFileIsFetchedAndParsed() = runTest {
        // The web resolves a `kind: 'chapters'` track off the item and parses it.
        // Nothing here read one, so an item whose markers arrive as a file beside
        // the film had no chapters however many the server sent.
        val player = playerWith(FakeFetcher().respondWith(body = chapterVtt))
        player.setup()

        player.queue(listOf(Episode(chapterFile = "https://films.test/ep-1.chapters.vtt")))
        yield()

        assertEquals(listOf("Cold open", "Titles"), player.chapters().map { it.title })
    }

    @Test
    fun anItemThatStatesItsChaptersIsNotOverruledByAFile() = runTest {
        // Otherwise which one wins depends on which arrived first, which is not
        // something a host can reason about.
        val player = playerWith(FakeFetcher().respondWith(body = chapterVtt))
        player.setup()

        player.queue(
            listOf(
                Episode(
                    chapters = listOf(Chapter(startTime = 0.0, title = "Stated outright")),
                    chapterFile = "https://films.test/ep-1.chapters.vtt",
                ),
            ),
        )
        yield()

        assertEquals(listOf("Stated outright"), player.chapters().map { it.title })
    }
}
