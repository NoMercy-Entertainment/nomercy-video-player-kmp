// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// A fake sender speaking through a fake transport — no wire, no Ktor, no
// Cast SDK. What this proves: ReceiverSession's rules hold no matter which
// transport hands it a command, and a transport needs nothing from
// ReceiverSession beyond the (senderId, ReceiverCommand) -> ReceiverOutcome
// seam Task 1 already defined.
private class FakeReceiverTransport : ReceiverTransport {
    private var handler: ((String, ReceiverCommand) -> ReceiverOutcome)? = null
    private var disconnectHandler: ((String) -> Unit)? = null
    var started: Boolean = false
        private set

    override fun start(commandHandler: (senderId: String, command: ReceiverCommand) -> ReceiverOutcome) {
        started = true
        handler = commandHandler
    }

    override fun stop() {
        started = false
        handler = null
    }

    override fun onDisconnect(handler: (senderId: String) -> Unit) {
        disconnectHandler = handler
    }

    val broadcasts: MutableList<ReceiverStateEvent> = mutableListOf()
    override fun broadcast(event: ReceiverStateEvent) {
        broadcasts += event
    }

    fun deliver(senderId: String, command: ReceiverCommand): ReceiverOutcome =
        handler?.invoke(senderId, command) ?: error("transport not started")

    fun disconnect(senderId: String) {
        disconnectHandler?.invoke(senderId)
    }
}

private class TransportRecordingPlayback : ReceiverPlayback {
    val applied: MutableList<ReceiverCommand> = mutableListOf()
    override fun apply(command: ReceiverCommand) {
        applied += command
    }
}

class ReceiverTransportTest {

    @Test
    fun aCommandDeliveredByTheTransportReachesTheSessionAndThePlayback() {
        val playback = TransportRecordingPlayback()
        val session = ReceiverSession(playback)
        val transport = FakeReceiverTransport()

        transport.start { senderId, command -> session.handle(senderId, command) }
        val outcome = transport.deliver("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        assertEquals(ReceiverOutcome.Accepted, outcome)
        assertEquals(listOf<ReceiverCommand>(ReceiverCommand.Launch("https://example.test/a.mkv")), playback.applied)
    }

    @Test
    fun aSecondSenderThroughTheSameTransportIsStillRefusedByTheSessionsOwnRule() {
        // The transport has no opinion on who's allowed to send — that rule
        // lives only in ReceiverSession, and this proves the transport
        // doesn't accidentally duplicate or bypass it.
        val playback = TransportRecordingPlayback()
        val session = ReceiverSession(playback)
        val transport = FakeReceiverTransport()
        transport.start { senderId, command -> session.handle(senderId, command) }
        transport.deliver("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        val outcome = transport.deliver("phone-2", ReceiverCommand.Pause)

        val refused = assertIs<ReceiverOutcome.Refused>(outcome)
        assertEquals(ReceiverRefusal.WRONG_SENDER, refused.reason)
    }

    @Test
    fun aTransportDisconnectReleasesTheSessionForTheNextSender() {
        val playback = TransportRecordingPlayback()
        val session = ReceiverSession(playback)
        val transport = FakeReceiverTransport()
        transport.start { senderId, command -> session.handle(senderId, command) }
        transport.onDisconnect { senderId -> session.release(senderId) }
        transport.deliver("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        transport.disconnect("phone-1")
        val outcome = transport.deliver("phone-2", ReceiverCommand.Launch("https://example.test/b.mkv"))

        assertEquals(ReceiverOutcome.Accepted, outcome)
    }

    @Test
    fun noReceiverTransportStartsAndStopsWithoutEverInvokingAHandler() {
        var invoked = false
        NoReceiverTransport.start { _, _ ->
            invoked = true
            ReceiverOutcome.Accepted
        }
        NoReceiverTransport.onDisconnect { invoked = true }
        NoReceiverTransport.stop()

        assertEquals(false, invoked)
    }

    @Test
    fun aBroadcastReachesWhicheverTransportIsListeningRegardlessOfWhoControls() {
        // Told, not asked: broadcast has no senderId, because who receives a
        // state snapshot is not the rule WRONG_SENDER governs.
        val playback = TransportRecordingPlayback()
        val session = ReceiverSession(playback)
        val transport = FakeReceiverTransport()
        transport.start { senderId, command -> session.handle(senderId, command) }
        val snapshot = ReceiverStateEvent(
            playbackState = "playing",
            positionMs = 1_000,
            durationMs = 60_000,
            itemId = "a",
            itemTitle = "A",
            audioTrackId = null,
            subtitleTrackId = null,
            qualityLabel = "auto",
            volumeLevel = 100,
            muted = false,
            playlistLength = 1,
            playlistActiveIndex = 0,
        )

        transport.broadcast(snapshot)

        assertEquals(listOf(snapshot), transport.broadcasts)
    }

    @Test
    fun noReceiverTransportBroadcastsToNobody() {
        // No assertion beyond "does not throw" is possible without a
        // listener to record it — which is exactly the point: there is
        // nobody for NoReceiverTransport to tell.
        NoReceiverTransport.broadcast(
            ReceiverStateEvent(
                playbackState = "idle",
                positionMs = 0,
                durationMs = 0,
                itemId = null,
                itemTitle = null,
                audioTrackId = null,
                subtitleTrackId = null,
                qualityLabel = "auto",
                volumeLevel = 0,
                muted = false,
                playlistLength = 0,
                playlistActiveIndex = -1,
            ),
        )
    }
}
