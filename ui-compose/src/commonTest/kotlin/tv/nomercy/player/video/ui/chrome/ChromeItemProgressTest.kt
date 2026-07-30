// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.video.item.VideoPlaylistItem
import tv.nomercy.player.video.item.WatchProgress
import tv.nomercy.player.video.tv.TvChromeItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// What the playlist card is given, from the item the host handed over.
//
// The normaliser has its own test; this grades the WIRING. The default projection
// read neither the runtime nor the progress, so a host passing a server payload
// and not overriding `itemOf` got a pane of bare titles — with the fields to draw
// them sitting on the item it had passed in. A test on the normaliser alone stays
// green through all of that.
class ChromeItemProgressTest {

    @Test
    fun anOlderServersProgressReachesTheCard() {
        val card: TvChromeItem? = chromeItemOf(
            LegacyEpisode(progress = WatchProgress(date = "2026-07-30T09:00:00Z", time = 300.0)),
        )

        // A quarter of twenty minutes. Read straight off the item this is null,
        // because the older shape carries no percentage at all.
        assertEquals(25, card?.progressPercent)
    }

    @Test
    fun theRuntimeReachesTheCardToo() {
        val card: TvChromeItem? = chromeItemOf(LegacyEpisode())

        assertEquals(1200.0, card?.durationSeconds)
    }

    @Test
    fun aCoreItemStillWorksAndClaimsNeither() {
        // The three-field item. Nothing is invented for it: an absent progress
        // draws no track at all, which is not the same as a track at nought.
        val card: TvChromeItem? = chromeItemOf(PlainItem())

        assertEquals("one", card?.id)
        assertNull(card?.progressPercent)
        assertNull(card?.durationSeconds)
    }

    @Test
    fun nothingPlayingIsNoCard() {
        assertNull(chromeItemOf(null))
    }
}

// A host's own episode, in the shape an older server sends.
private data class LegacyEpisode(
    override val id: String = "s01e04",
    override val url: String = "file://s01e04.mkv",
    override val title: String? = "The Quiet Earth",
    override val durationSeconds: Double? = 1200.0,
    override val progress: WatchProgress? = null,
) : VideoPlaylistItem

private data class PlainItem(
    override val id: String = "one",
    override val url: String = "file://one.mkv",
    override val title: String? = "One",
) : PlaylistItem
