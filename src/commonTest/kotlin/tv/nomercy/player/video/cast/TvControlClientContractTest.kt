// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The shape of the conversation with a television.
//
// What is pinned here is the seam rather than any implementation of it: the
// units it speaks, the things it is allowed to leave unsaid, and the fact that
// the stream is a stream. The Ktor client and the controller are both held to
// this, so a change that suits one and breaks the other shows up here first.
class TvControlClientContractTest {

    @Test
    fun aSeekCrossesInMillisecondsBecauseThatIsTheSetsUnit() {
        // The player counts in seconds and the television in milliseconds. The
        // conversion happens once above this seam; getting it wrong here is a
        // scrubber that jumps by a factor of a thousand.
        runTest {
            val client = FakeTvControlClient()

            client.seek(90_000)

            assertEquals(listOf("seek:90000"), client.commands)
        }
    }

    @Test
    fun aVolumeChangeMaySayOnlyWhatChanged() {
        // A mute button should not have to know the current level in order to
        // press itself, and a volume slider should not have to state the mute.
        runTest {
            val client = FakeTvControlClient()

            client.setVolume(muted = true)
            client.setVolume(level = 0.4)

            assertEquals(listOf("volume:null:null:true", "volume:0.4:null:null"), client.commands)
        }
    }

    @Test
    fun turningSubtitlesOffIsNotTheSameAsAnEmptyTrack() {
        // Null means off. An empty id would be a track the set has to look up
        // and fail to find, which is a different outcome and a worse one.
        runTest {
            val client = FakeTvControlClient()

            client.setSubtitleTrack(null)
            client.setSubtitleTrack("en")

            assertEquals(listOf("subtitle:null", "subtitle:en"), client.commands)
        }
    }

    @Test
    fun aTelevisionMayRefuseToLaunchSomething() {
        // Busy, or asked for something it cannot play. A caller assuming success
        // leaves a phone showing a casting state with nothing on the screen.
        runTest {
            val client = FakeTvControlClient(launchAccepted = false)

            assertFalse(client.launch("https://media.example.test/film.mkv"))
        }
    }

    @Test
    fun theHandshakeAnswersWithoutACredential() {
        // A picker has to list a set it has never talked to. Requiring a token
        // first means a television that is invisible until it is already
        // trusted, which is the wrong way round.
        runTest {
            val client = FakeTvControlClient(server = RemoteServer(serverName = "Living Room"))

            assertEquals("Living Room", client.getServer().serverName)
        }
    }

    @Test
    fun aCapabilityThisBuildHasNeverHeardOfIsCarriedRatherThanRejected() {
        // Capabilities are the set's vocabulary, not the phone's. A newer
        // television advertising something unknown should still be listed.
        runTest {
            val client = FakeTvControlClient(
                server = RemoteServer(capabilities = listOf("cast", "somethingAddedLater")),
            )

            assertTrue(client.getServer().capabilities.contains("somethingAddedLater"))
        }
    }

    @Test
    fun theSessionCanBeAskedForRatherThanWaitedFor() {
        // A phone joining a session already in progress shows the right thing
        // immediately instead of staying blank until the set next changes
        // something — which, on a paused film, could be a long time.
        runTest {
            val client = FakeTvControlClient(
                session = RemotePlayerState(itemTitle = "Blade Runner 2049", positionMs = 42_000),
            )

            val state: RemotePlayerState = client.getSession()

            assertEquals("Blade Runner 2049", state.itemTitle)
            assertEquals(42_000, state.positionMs)
        }
    }

    @Test
    fun whatTheSetDoesOnItsOwnArrivesOnTheStream() {
        // Another remote, or someone at the television itself. The phone is one
        // of several things that can move this session and has to follow rather
        // than assume.
        runTest {
            val client = FakeTvControlClient()

            val received = async { client.events().first() }
            testScheduler.advanceUntilIdle()
            client.emit(RemoteEvent.Transport("paused"))

            assertEquals(RemoteEvent.Transport("paused"), received.await())
        }
    }
}
