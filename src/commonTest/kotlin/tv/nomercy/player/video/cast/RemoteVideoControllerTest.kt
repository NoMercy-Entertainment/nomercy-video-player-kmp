// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The television as one object, driven and followed.
//
// Everything here runs against the fake, which is the point: the controller is
// the piece with the reconciliation in it, and reconciliation is exactly what a
// test with a real set on the other end proves slowly and unreliably.
class RemoteVideoControllerTest {

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private suspend fun TestScope.connected(
        client: FakeTvControlClient,
    ): RemoteVideoController = RemoteVideoController(client, eager()).also { it.connect() }

    @Test
    fun theSessionIsAskedForBeforeAnythingIsAwaited() = runTest {
        // A phone joining a film already playing shows it immediately. Waiting
        // for the first frame instead means a blank chrome for as long as the
        // viewer stays paused.
        val client = FakeTvControlClient(
            session = RemotePlayerState(itemTitle = "Blade Runner 2049", positionMs = 42_000),
        )

        val controller: RemoteVideoController = connected(client)

        assertEquals("Blade Runner 2049", controller.state.value?.itemTitle)
        assertEquals(RemoteStatus.CONNECTED, controller.status.value)
    }

    @Test
    fun aFullFrameReplacesWhatWasKnown() = runTest {
        val client = FakeTvControlClient(session = RemotePlayerState(itemTitle = "First"))
        val controller: RemoteVideoController = connected(client)

        client.emit(RemoteEvent.State(RemotePlayerState(itemTitle = "Second")))

        assertEquals("Second", controller.state.value?.itemTitle)
    }

    @Test
    fun aTransportFrameMovesOnlyTheTransport() = runTest {
        // The set reports a pause without repeating the whole session. Ignoring
        // it leaves a play button on a paused film; replacing everything with it
        // throws the title and position away.
        val client = FakeTvControlClient(
            session = RemotePlayerState(
                itemTitle = "Blade Runner 2049",
                positionMs = 42_000,
                playbackState = RemotePlaybackState.PLAYING,
            ),
        )
        val controller: RemoteVideoController = connected(client)

        client.emit(RemoteEvent.Transport("paused"))

        assertEquals(RemotePlaybackState.PAUSED, controller.state.value?.playbackState)
        assertEquals("Blade Runner 2049", controller.state.value?.itemTitle)
        assertEquals(42_000, controller.state.value?.positionMs)
    }

    @Test
    fun aTransportWordThisBuildDoesNotKnowChangesNothing() = runTest {
        // A newer set doing something unfamiliar. Falling back to a default
        // would draw it as idle, which is worse than leaving the last known
        // state up until a frame this build understands arrives.
        val client = FakeTvControlClient(
            session = RemotePlayerState(playbackState = RemotePlaybackState.PLAYING),
        )
        val controller: RemoteVideoController = connected(client)

        client.emit(RemoteEvent.Transport("rewinding"))

        assertEquals(RemotePlaybackState.PLAYING, controller.state.value?.playbackState)
    }

    @Test
    fun aTrackFrameChangesOnlyTheTrackItNames() = runTest {
        val client = FakeTvControlClient(
            session = RemotePlayerState(audioTrackId = "jpn", subtitleTrackId = ""),
        )
        val controller: RemoteVideoController = connected(client)

        client.emit(RemoteEvent.Track(audioTrackId = null, subtitleTrackId = "eng"))

        assertEquals("jpn", controller.state.value?.audioTrackId, "the audio track was changed too")
        assertEquals("eng", controller.state.value?.subtitleTrackId)
    }

    @Test
    fun aSeekIsSentInTheSetsUnitRatherThanThePlayers() = runTest {
        // Seconds here, milliseconds on the wire. Getting it wrong is a
        // scrubber that jumps by a factor of a thousand.
        val client = FakeTvControlClient()
        val controller: RemoteVideoController = connected(client)

        controller.seek(42.0)

        assertEquals("seek:42000", client.commands.last())
    }

    @Test
    fun volumeIsSentInTheSetsScaleRatherThanThePlayers() = runTest {
        // Out of a hundred here, out of one on the wire.
        val client = FakeTvControlClient()
        val controller: RemoteVideoController = connected(client)

        controller.presentation.setVolumePercent(50)

        assertEquals("volume:0.5:null:null", client.commands.last())
    }

    @Test
    fun aVolumeOutsideTheScaleIsBroughtBackIntoIt() = runTest {
        // A chrome with an off-by-one slider should not be able to ask a
        // television for a hundred and ten percent.
        val client = FakeTvControlClient()
        val controller: RemoteVideoController = connected(client)

        controller.presentation.setVolumePercent(150)
        controller.presentation.setVolumePercent(-20)

        assertEquals(listOf("volume:1.0:null:null", "volume:0.0:null:null"), client.commands.takeLast(2))
    }

    @Test
    fun theSetSayingItIsDoneEndsTheSession() = runTest {
        // Switched off, switched input, or another phone took it. Carrying on
        // leaves a chrome offering control of something that stopped listening.
        val client = FakeTvControlClient()
        val controller: RemoteVideoController = connected(client)

        client.emit(RemoteEvent.Device("disconnected"))

        assertEquals(RemoteStatus.DISCONNECTED, controller.status.value)
    }

    @Test
    fun aDeviceFrameThatIsNotADisconnectDoesNotEndTheSession() = runTest {
        val client = FakeTvControlClient()
        val controller: RemoteVideoController = connected(client)

        client.emit(RemoteEvent.Device("volumeChanged"))

        assertEquals(RemoteStatus.CONNECTED, controller.status.value)
    }

    @Test
    fun anErrorFrameDoesNotByItselfEndTheSession() = runTest {
        // A stream hiccup on the set is not a disconnection. Ending here would
        // drop a viewer out of a working session over one bad frame.
        val client = FakeTvControlClient()
        val controller: RemoteVideoController = connected(client)

        client.emit(RemoteEvent.Failure("decoder-hiccup"))

        assertEquals(RemoteStatus.CONNECTED, controller.status.value)
    }

    @Test
    fun nothingIsKnownBeforeConnecting() = runTest {
        val controller = RemoteVideoController(FakeTvControlClient(), eager())

        assertNull(controller.state.value)
        assertEquals(RemoteStatus.DISCONNECTED, controller.status.value)
    }

    @Test
    fun disconnectingStopsFollowingTheSet() = runTest {
        // The subscription has to end with the session. A controller still
        // collecting after a disconnect updates a chrome that has been torn
        // down, and on a phone that is a leak per cast.
        val client = FakeTvControlClient(session = RemotePlayerState(itemTitle = "First"))
        val controller: RemoteVideoController = connected(client)

        controller.disconnect()
        client.emit(RemoteEvent.State(RemotePlayerState(itemTitle = "Second")))

        assertEquals("First", controller.state.value?.itemTitle, "a disconnected controller kept listening")
    }
}
