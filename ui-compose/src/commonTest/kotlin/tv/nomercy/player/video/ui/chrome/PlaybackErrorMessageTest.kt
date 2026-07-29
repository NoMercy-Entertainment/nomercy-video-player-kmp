// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The error table, against the app's own getUserFriendlyMessage.
//
// A lookup that has drifted is invisible on screen: every message still
// appears, each one just describes the wrong failure. So the codes are checked
// by value rather than by "some sentence came back".
class PlaybackErrorMessageTest {

    @Test
    fun aDecoderCodeSaysWhatTheDecoderCouldNotDo() {
        assertEquals(
            "This video file cannot be played. The format exceeds device capabilities.",
            PlaybackErrorMessage.forError("4003", "PlaybackException"),
        )
    }

    @Test
    fun aNetworkCodeBlamesTheConnectionAndNotTheFile() {
        assertEquals(
            "Network timeout. Please check your internet connection.",
            PlaybackErrorMessage.forError("2001", null),
        )
    }

    @Test
    fun aDrmCodeSaysProtectedRatherThanBroken() {
        assertEquals("DRM license acquisition failed.", PlaybackErrorMessage.forError("6001", null))
    }

    // Every code in the table answers with its own sentence, and no two share
    // one by accident — a copy-paste that duplicated a row would tell a viewer
    // the wrong thing on exactly one failure and nothing would catch it.
    @Test
    fun theFourRangesAreAllPresentAndDistinct() {
        val codes = listOf(
            1000, 1001, 1002, 1003,
            2000, 2001, 2002,
            4001, 4002, 4003, 4004,
            5001, 5002, 5003,
            6000, 6001, 6002, 6003,
        )

        val messages = codes.map { PlaybackErrorMessage.forError(it.toString(), null) }

        assertTrue(messages.none { it == "Unknown error" }, "a code fell through to the fallback")

        // Eighteen codes, eighteen different sentences. A copy-paste that
        // duplicated a row would describe the wrong failure on exactly one code
        // and nothing on screen would look wrong.
        assertEquals(codes.size, messages.toSet().size)
    }

    // The fallback is what runs when a backend reports a failure with no code,
    // which is most of them outside Media3.
    @Test
    fun aCodelessDecoderFailureIsStillReadAsOne() {
        assertEquals(
            "This video file cannot be played.",
            PlaybackErrorMessage.forError(null, "MediaCodec decoder init failed"),
        )
    }

    @Test
    fun aCodelessNetworkFailureBlamesTheConnection() {
        assertEquals(
            "This video cannot be played because of a problem with your internet connection.",
            PlaybackErrorMessage.forError(null, "Connection reset by peer"),
        )
    }

    // Codec beats network when a message mentions both, which is the app's
    // ordering: a decoder that failed while streaming is a decoder problem.
    @Test
    fun theMoreSpecificFailureWinsWhenAMessageMentionsBoth() {
        assertEquals(
            "This video file cannot be played.",
            PlaybackErrorMessage.forError(null, "codec failure during network read"),
        )
    }

    @Test
    fun nothingRecognisedIsSaidPlainly() {
        assertEquals("Unknown error", PlaybackErrorMessage.forError(null, null))
        assertEquals("Unknown error", PlaybackErrorMessage.forError("99999", "something new"))
    }
}
