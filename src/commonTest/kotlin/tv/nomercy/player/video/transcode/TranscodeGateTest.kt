// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.transcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranscodeGateTest {

    private fun progress(seconds: Double, job: String = "job-1") =
        TranscodeMessage.Progress(TranscodeProgress(job, seconds))

    @Test
    fun nothingIsEncodedBeforeTheFirstProgress() {
        assertEquals(0.0, TranscodeGate().transcodedTo())
    }

    @Test
    fun aSeekBehindTheWriteHeadGoesStraightThrough() {
        val gate = TranscodeGate()
        gate.accept(progress(120.0))

        assertEquals(SeekVerdict.Ready, gate.verdictFor(60.0))
        assertEquals(SeekVerdict.Ready, gate.verdictFor(120.0))
    }

    @Test
    fun aSeekPastTheWriteHeadWaits() {
        val gate = TranscodeGate()
        gate.accept(progress(120.0))

        assertEquals(SeekVerdict.Waiting, gate.verdictFor(121.0))
    }

    // The web resolves rather than rejects on timeout, and that is right:
    // blocking forever on a server that stopped answering is worse than a short
    // read. A viewer can retry a stutter, not a frozen player.
    @Test
    fun theWaitGivesUpRatherThanHangingThePlayer() {
        val gate = TranscodeGate(seekTimeoutMs = 1_000)

        assertEquals(SeekVerdict.Waiting, gate.verdictFor(500.0, waitedMs = 999))
        assertEquals(SeekVerdict.TimedOut, gate.verdictFor(500.0, waitedMs = 1_000))
    }

    // A late progress message from before a seek would otherwise move the head
    // back and gate a position the server has already passed.
    @Test
    fun theWriteHeadNeverGoesBackwards() {
        val gate = TranscodeGate()
        gate.accept(progress(120.0))
        gate.accept(progress(60.0))

        assertEquals(120.0, gate.transcodedTo())
    }

    @Test
    fun aNewItemResetsTheHead() {
        val gate = TranscodeGate()
        gate.accept(progress(120.0))
        gate.startItem("job-2")

        assertEquals(0.0, gate.transcodedTo())
        assertEquals("job-2", gate.currentJob())
    }

    @Test
    fun eachStatusTypeParses() {
        assertEquals(
            TranscodeMessage.Started("j", "https://example/x.mkv"),
            parseTranscodeMessage("started", jobId = "j", sourceUrl = "https://example/x.mkv"),
        )
        assertEquals(
            TranscodeMessage.ReadyToPlay("j"),
            parseTranscodeMessage("ready-to-play", jobId = "j"),
        )
        assertEquals(
            TranscodeMessage.Complete("j"),
            parseTranscodeMessage("complete", jobId = "j"),
        )
    }

    // The web's `typeof msg.transcodedSeconds === 'number'`. Treating a missing
    // number as zero would move the head backwards.
    @Test
    fun aProgressMessageWithoutANumberIsNotProgress() {
        assertNull(parseTranscodeMessage("progress", jobId = "j"))
    }

    // Kept rather than dropped, so a server that grows a new status shows up in
    // a log instead of being indistinguishable from a message that never came.
    @Test
    fun anUnknownTypeIsReportedRatherThanSwallowed() {
        assertEquals(
            TranscodeMessage.Unknown("paused-by-operator"),
            parseTranscodeMessage("paused-by-operator"),
        )
    }

    @Test
    fun aMessageWithNoTypeIsNothing() {
        assertNull(parseTranscodeMessage(null))
    }

    @Test
    fun progressCarriesTheVariantsAndTotal() {
        val message = parseTranscodeMessage(
            "progress",
            jobId = "j",
            transcodedSeconds = 30.0,
            totalSeconds = 600.0,
            variantsReady = listOf("1080p", "720p"),
        )

        assertEquals(
            TranscodeMessage.Progress(
                TranscodeProgress("j", 30.0, 600.0, listOf("1080p", "720p")),
            ),
            message,
        )
    }
}
