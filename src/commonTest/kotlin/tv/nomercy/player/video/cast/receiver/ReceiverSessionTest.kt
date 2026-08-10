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

private class RecordingPlayback(private val playable: Boolean = true) : ReceiverPlayback {
    val applied: MutableList<ReceiverCommand> = mutableListOf()

    override fun apply(command: ReceiverCommand) {
        applied += command
    }

    override fun canPlay(url: String): Boolean = playable
}

class ReceiverSessionTest {

    @Test
    fun aFirstLaunchIsAcceptedAndTakesTheSession() {
        val playback = RecordingPlayback()
        val session = ReceiverSession(playback)

        val outcome: ReceiverOutcome = session.handle("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        assertEquals(ReceiverOutcome.Accepted, outcome)
        assertEquals(listOf<ReceiverCommand>(ReceiverCommand.Launch("https://example.test/a.mkv")), playback.applied)
    }

    @Test
    fun aLoadForContentThisDeviceCannotPlayIsRefused() {
        val playback = RecordingPlayback(playable = false)
        val session = ReceiverSession(playback)

        val outcome: ReceiverOutcome = session.handle("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        val refused: ReceiverOutcome.Refused = assertIs<ReceiverOutcome.Refused>(outcome)
        assertEquals(ReceiverRefusal.CANNOT_PLAY, refused.reason)
        assertEquals(emptyList(), playback.applied, "a refused launch still reached the player")
    }

    @Test
    fun aCommandArrivingBeforeAnythingIsLoadedIsRefused() {
        val playback = RecordingPlayback()
        val session = ReceiverSession(playback)

        val outcome: ReceiverOutcome = session.handle("phone-1", ReceiverCommand.Play)

        val refused: ReceiverOutcome.Refused = assertIs<ReceiverOutcome.Refused>(outcome)
        assertEquals(ReceiverRefusal.NOTHING_LOADED, refused.reason)
    }

    @Test
    fun aSecondSenderWhileOneIsAlreadyControllingIsRefused() {
        val playback = RecordingPlayback()
        val session = ReceiverSession(playback)
        session.handle("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        val outcome: ReceiverOutcome = session.handle("phone-2", ReceiverCommand.Launch("https://example.test/b.mkv"))

        val refused: ReceiverOutcome.Refused = assertIs<ReceiverOutcome.Refused>(outcome)
        assertEquals(ReceiverRefusal.ALREADY_CONTROLLED, refused.reason)
    }

    @Test
    fun aSecondSendersTransportCommandIsRefusedEvenAfterALoad() {
        // The rule this whole class exists for, stated the other way: not
        // just a second Launch, but any verb from a sender that is not the
        // one controlling.
        val playback = RecordingPlayback()
        val session = ReceiverSession(playback)
        session.handle("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        val outcome: ReceiverOutcome = session.handle("phone-2", ReceiverCommand.Pause)

        val refused: ReceiverOutcome.Refused = assertIs<ReceiverOutcome.Refused>(outcome)
        assertEquals(ReceiverRefusal.WRONG_SENDER, refused.reason)
        assertEquals(listOf<ReceiverCommand>(ReceiverCommand.Launch("https://example.test/a.mkv")), playback.applied)
    }

    @Test
    fun theControllingSendersCommandsAllReachThePlayer() {
        val playback = RecordingPlayback()
        val session = ReceiverSession(playback)
        session.handle("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        session.handle("phone-1", ReceiverCommand.Pause)
        session.handle("phone-1", ReceiverCommand.Seek(30_000))
        session.handle("phone-1", ReceiverCommand.Play)

        assertEquals(
            listOf(
                ReceiverCommand.Launch("https://example.test/a.mkv"),
                ReceiverCommand.Pause,
                ReceiverCommand.Seek(30_000),
                ReceiverCommand.Play,
            ),
            playback.applied,
        )
    }

    @Test
    fun releasingTheControllingSenderFreesTheSessionForALaunchFromAnyoneElse() {
        val playback = RecordingPlayback()
        val session = ReceiverSession(playback)
        session.handle("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        session.release("phone-1")
        val outcome: ReceiverOutcome = session.handle("phone-2", ReceiverCommand.Launch("https://example.test/b.mkv"))

        assertEquals(ReceiverOutcome.Accepted, outcome)
    }

    @Test
    fun aStaleReleaseFromASenderThatAlreadyLostTheSessionDoesNothing() {
        val playback = RecordingPlayback()
        val session = ReceiverSession(playback)
        session.handle("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))
        session.release("phone-1")
        session.handle("phone-2", ReceiverCommand.Launch("https://example.test/b.mkv"))

        // phone-1's own release, arriving late over an unordered transport —
        // must not evict phone-2, who holds the session now.
        session.release("phone-1")

        val outcome: ReceiverOutcome = session.handle("phone-2", ReceiverCommand.Pause)
        assertEquals(ReceiverOutcome.Accepted, outcome)
    }

    @Test
    fun isControlledByAnswersForTheCurrentSenderOnly() {
        val session = ReceiverSession(RecordingPlayback())
        session.handle("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        assertEquals(true, session.isControlledBy("phone-1"))
        assertEquals(false, session.isControlledBy("phone-2"))
    }

    @Test
    fun hasContentIsFalseUntilALaunchLands() {
        val session = ReceiverSession(RecordingPlayback())

        assertEquals(false, session.hasContent())

        session.handle("phone-1", ReceiverCommand.Launch("https://example.test/a.mkv"))

        assertEquals(true, session.hasContent())
    }
}
