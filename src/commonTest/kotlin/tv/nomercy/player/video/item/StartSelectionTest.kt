// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private data class Episode(
    override val id: String,
    override val url: String = "https://media.example.test/$id.mkv",
    override val title: String? = null,
    override val durationSeconds: Double? = 2400.0,
    override val progress: WatchProgress? = null,
) : VideoPlaylistItem

// Where an autoplaying queue starts.
//
// WatchProgress and its normaliser were both here and tested before this was,
// and nothing read them to decide anything: a native player autoplaying a
// season began at episode one from zero, whatever the viewer had watched.
class StartSelectionTest {

    @Test
    fun aQueueNobodyHasWatchedStartsAtTheTop() {
        val selection = pickStartItem(listOf(Episode("a"), Episode("b")))

        assertEquals(0, selection.index)
        assertNull(selection.resumeSeconds, "an unwatched queue was told to seek")
    }

    @Test
    fun theMostRecentlyWatchedItemWinsRatherThanTheFirstOrTheFurthestThrough() {
        // Deliberately arranged so "first with progress" and "furthest through"
        // both answer a, and only "most recent" answers c.
        val items = listOf(
            Episode("a", progress = WatchProgress(timestamp = 100L, percentage = 80.0, time = 1920.0)),
            Episode("b"),
            Episode("c", progress = WatchProgress(timestamp = 900L, percentage = 10.0, time = 240.0)),
        )

        val selection = pickStartItem(items)

        assertEquals(2, selection.index)
        assertEquals(240.0, selection.resumeSeconds)
    }

    @Test
    fun anItemPastNinetyPercentRollsOverToTheNextOneAndStartsItFresh() {
        // Resuming four minutes from the end of an episode somebody finished is
        // the case this rule exists for.
        val items = listOf(
            Episode("a", progress = WatchProgress(timestamp = 100L, percentage = 96.0, time = 2304.0)),
            Episode("b"),
        )

        val selection = pickStartItem(items)

        assertEquals(1, selection.index)
        assertNull(selection.resumeSeconds, "the next episode was resumed at the previous one's position")
    }

    @Test
    fun exactlyNinetyResumesRatherThanRollingOver() {
        // The reference tests strictly greater. A threshold that rounded the
        // other way would skip an episode a viewer was ten percent short of.
        val items = listOf(
            Episode("a", progress = WatchProgress(timestamp = 100L, percentage = 90.0, time = 2160.0)),
            Episode("b"),
        )

        val selection = pickStartItem(items)

        assertEquals(0, selection.index)
        assertEquals(2160.0, selection.resumeSeconds)
    }

    @Test
    fun theLastItemInAQueueHasNothingToRollOverIntoSoItStays() {
        val items = listOf(
            Episode("a"),
            Episode("b", progress = WatchProgress(timestamp = 100L, percentage = 99.0, time = 2376.0)),
        )

        val selection = pickStartItem(items)

        assertEquals(1, selection.index, "the cursor rolled off the end of the queue")
    }

    @Test
    fun aStoredPositionOfZeroIsNotASeek() {
        // Distinct from "resume at zero": the caller passes this straight to the
        // load as a start position, and a zero would be a round trip asking the
        // engine to go where it already is.
        val items = listOf(Episode("a", progress = WatchProgress(timestamp = 100L, percentage = 0.0, time = 0.0)))

        assertNull(pickStartItem(items).resumeSeconds)
    }

    @Test
    fun aSessionWithNoReadableDateDoesNotBeatOneThatKnowsWhenItHappened() {
        // The older wire shape with a date nothing could parse normalises to a
        // null timestamp. Sorting it first would resume the wrong episode.
        val items = listOf(
            Episode("a", progress = WatchProgress(percentage = 20.0, time = 480.0)),
            Episode("b", progress = WatchProgress(timestamp = 50L, percentage = 30.0, time = 720.0)),
        )

        val selection = pickStartItem(items)

        assertEquals(1, selection.index)
        assertEquals(720.0, selection.resumeSeconds)
    }
}
