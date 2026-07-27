// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Turning a television's line-by-line event stream into frames.
//
// The part of casting with states in it, and the part a connected test finds
// slowly: a bug here is a set whose events stop being understood partway through
// an evening. Fed by hand so each case is one line at a time.
class SseAccumulatorTest {

    private fun feedAll(lines: List<String>): List<Pair<String, String>> {
        val accumulator = SseAccumulator()

        return lines.mapNotNull(accumulator::feed)
    }

    @Test
    fun aNamedFrameArrivesWithItsNameAndItsPayload() {
        val frames: List<Pair<String, String>> = feedAll(
            listOf("event:transport", """data:{"transport":"paused"}""", ""),
        )

        assertEquals(listOf("transport" to """{"transport":"paused"}"""), frames)
    }

    @Test
    fun anUnnamedFrameIsAStateFrame() {
        // Which is what a television sends most of, and what the clients that
        // already talk to one assume.
        val frames: List<Pair<String, String>> = feedAll(listOf("""data:{"positionMs":1000}""", ""))

        assertEquals(listOf("state" to """{"positionMs":1000}"""), frames)
    }

    @Test
    fun aPayloadSplitAcrossLinesIsJoinedWithNewlines() {
        // One JSON document with its own line breaks. Gluing the halves together
        // makes a string that parses as nothing.
        val frames: List<Pair<String, String>> = feedAll(listOf("data:{", """data:"a":1""", "data:}", ""))

        assertEquals(listOf("state" to "{\n\"a\":1\n}"), frames)
    }

    @Test
    fun aKeepAliveIsNotAnEmptyFrame() {
        // Servers send a bare blank line so something in the middle does not
        // close the connection for being idle. Treating one as a frame puts a
        // null state on screen every fifteen seconds.
        assertEquals(emptyList(), feedAll(listOf("", "", "")))
    }

    @Test
    fun aNameWithNoPayloadIsDroppedRatherThanEmitted() {
        // Half a frame. Emitting it means the decoder is handed an empty string
        // to parse as a player state.
        assertEquals(emptyList(), feedAll(listOf("event:state", "")))
    }

    @Test
    fun oneFramesNameDoesNotLeakIntoTheNext() {
        // The failure this class exists to prevent, and the one that only shows
        // up on the second event: a name held past its own blank line makes
        // every later state frame decode as whatever came before it.
        val frames: List<Pair<String, String>> = feedAll(
            listOf("event:transport", "data:{}", "", "data:{}", ""),
        )

        assertEquals(listOf("transport" to "{}", "state" to "{}"), frames)
    }

    @Test
    fun oneFramesPayloadDoesNotLeakIntoTheNext() {
        val frames: List<Pair<String, String>> = feedAll(
            listOf("data:first", "", "data:second", ""),
        )

        assertEquals(listOf("state" to "first", "state" to "second"), frames)
    }

    @Test
    fun aCommentOrAnythingElseIsIgnored() {
        // Streams carry retry hints and comments. Neither is a frame and neither
        // should end up inside one.
        val frames: List<Pair<String, String>> = feedAll(
            listOf(":ping", "retry:5000", "data:payload", ""),
        )

        assertEquals(listOf("state" to "payload"), frames)
    }

    @Test
    fun nothingIsReturnedUntilTheBlankLineArrives() {
        // The frame is not finished before it is closed, however complete it
        // looks. Emitting early means a partial payload reaching the decoder.
        val accumulator = SseAccumulator()

        assertNull(accumulator.feed("event:state"))
        assertNull(accumulator.feed("""data:{"positionMs":1}"""))
        assertEquals("state" to """{"positionMs":1}""", accumulator.feed(""))
    }
}
