// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// Decoding what a television says about itself.
//
// The compatibility rules live here, and they are not symmetrical: a frame this
// build does not recognise has to survive, and a frame it recognises but cannot
// parse has to be dropped without ending the subscription. Self-hosted sets
// update on their own schedule, so the two ends being different ages is the
// normal case rather than the edge one.
class RemoteEventTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aStateFrameDecodesToWhatTheTelevisionIsDoing() {
        val event: RemoteEvent? = decodeRemoteEvent(
            "state",
            """{"sessionId":"s1","playbackState":"playing","positionMs":42000,"itemTitle":"Blade Runner 2049"}""",
            json,
        )

        val state = assertIs<RemoteEvent.State>(event)
        assertEquals(RemotePlaybackState.PLAYING, state.state.playbackState)
        assertEquals(42_000, state.state.positionMs)
        assertEquals("Blade Runner 2049", state.state.itemTitle)
    }

    @Test
    fun anOlderTelevisionsPartialFrameStillDecodes() {
        // Every field has a default for this reason. A set running last year's
        // build sends what it knows about, and a phone that threw on the rest
        // would refuse to control something working perfectly well.
        val event: RemoteEvent? = decodeRemoteEvent("state", """{"sessionId":"s1"}""", json)

        val state = assertIs<RemoteEvent.State>(event)
        assertEquals("s1", state.state.sessionId)
        assertEquals(RemotePlaybackState.IDLE, state.state.playbackState)
        assertEquals(1.0, state.state.volume)
    }

    @Test
    fun aNewerTelevisionsExtraFieldsAreIgnoredRatherThanFatal()
    {
        // The other direction of the same problem: the set is ahead of the
        // phone. An unknown key must not take the frame down.
        val event: RemoteEvent? = decodeRemoteEvent(
            "state",
            """{"sessionId":"s1","somethingAddedLater":{"nested":true}}""",
            json,
        )

        assertEquals("s1", assertIs<RemoteEvent.State>(event).state.sessionId)
    }

    @Test
    fun eachNamedFrameDecodesToItsOwnShape() {
        assertEquals(
            RemoteEvent.Transport("paused"),
            decodeRemoteEvent("transport", """{"transport":"paused"}""", json),
        )
        assertEquals(
            RemoteEvent.Device("disconnected"),
            decodeRemoteEvent("device", """{"action":"disconnected"}""", json),
        )
        assertEquals(
            RemoteEvent.Failure("stream-gone"),
            decodeRemoteEvent("error", """{"reason":"stream-gone"}""", json),
        )
    }

    @Test
    fun aTrackFrameCarriesEitherTrackOrNeither() {
        // A television changing only the subtitle sends only that, and null is
        // the difference between "unchanged" and "turned off".
        val event: RemoteEvent? = decodeRemoteEvent("track", """{"subtitleTrackId":"en"}""", json)

        val track = assertIs<RemoteEvent.Track>(event)
        assertNull(track.audioTrackId)
        assertEquals("en", track.subtitleTrackId)
    }

    @Test
    fun aPlaylistFrameCarriesItsItemsAndWhichIsPlaying() {
        val event: RemoteEvent? = decodeRemoteEvent(
            "playlist",
            """{"activeIndex":1,"items":[{"id":"a","title":"One"},{"id":"b","title":"Two"}]}""",
            json,
        )

        val playlist = assertIs<RemoteEvent.Playlist>(event)
        assertEquals(1, playlist.playlist.activeIndex)
        assertEquals(listOf("One", "Two"), playlist.playlist.items.map { it.title })
    }

    @Test
    fun aFrameThisBuildDoesNotKnowIsNamedRatherThanDropped() {
        // A television ahead of this phone sending something new. Naming it
        // means a log line that says what arrived; dropping it silently means
        // a feature that appears not to exist.
        assertEquals(
            RemoteEvent.Unknown("somethingNew"),
            decodeRemoteEvent("somethingNew", "{}", json),
        )
    }

    @Test
    fun aMalformedPayloadIsDroppedRatherThanThrown() {
        // The stream is one long connection to a device across a house. An
        // exception here ends the whole subscription over a single bad frame:
        // the television carries on playing and the phone stops listening.
        assertNull(decodeRemoteEvent("state", "not json at all", json))
        assertNull(decodeRemoteEvent("state", "", json))
        assertNull(decodeRemoteEvent("transport", """{"transport":]""", json))
    }

    @Test
    fun aStateThisBuildHasNeverHeardOfDropsTheFrameRatherThanGuessing() {
        // A newer television reporting a playback state this build does not
        // know. Serialization can be told to fall back to the default instead,
        // and that is the worse answer here: the default is idle, so a set that
        // is playing would be drawn as stopped. Dropping leaves the last known
        // state on screen — out of date rather than contradicted, and the next
        // frame the phone does understand corrects it.
        assertNull(decodeRemoteEvent("state", """{"playbackState":"rewinding"}""", json))
    }
}
