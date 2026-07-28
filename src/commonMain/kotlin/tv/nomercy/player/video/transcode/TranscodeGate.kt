// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.transcode

// Whether the server has encoded far enough to play from here yet.
//
// From the web's `live-transcoding` plugin. A NoMercy server transcodes on
// demand, so seeking past the encoder's write head asks for bytes that do not
// exist — and what a player does about that is the whole plugin.
//
// The rules are here and the transport is not, deliberately. The web opens a
// WebSocket; a native app already holds a SignalR connection to the same server
// and should not open a second one to hear the same numbers. What both need is
// the same: parse a status message, track the write head, and decide whether a
// seek can proceed.

/** How far the encoder has got, and for which job. */
public data class TranscodeProgress(
    val jobId: String,
    val transcodedSeconds: Double,
    val totalSeconds: Double? = null,
    val variantsReady: List<String> = emptyList(),
)

/** What the server said. */
public sealed interface TranscodeMessage {
    public data class Started(val jobId: String, val sourceUrl: String) : TranscodeMessage

    public data class Progress(val progress: TranscodeProgress) : TranscodeMessage

    public data class ReadyToPlay(val jobId: String) : TranscodeMessage

    public data class Complete(val jobId: String) : TranscodeMessage

    /**
     * A message this build does not know about.
     *
     * Kept rather than dropped so a server that grows a new status is visible
     * in a log instead of silently ignored — the web's `default: break` makes
     * an unknown type indistinguishable from a message that never arrived.
     */
    public data class Unknown(val type: String) : TranscodeMessage
}

/** The gate's answer for one seek. */
public enum class SeekVerdict {
    /** The target is already encoded. */
    Ready,

    /** Not encoded yet, and the encoder is still moving. Wait. */
    Waiting,

    /**
     * Not encoded and the wait ran out.
     *
     * The web resolves rather than rejects here, and that is the right call:
     * blocking a player forever on a server that stopped answering is worse
     * than letting the seek through and letting the engine deal with a short
     * read. A viewer can retry a stutter; they cannot retry a frozen player.
     */
    TimedOut,
}

/**
 * The encoder's write head, updated by whatever transport is carrying the
 * status messages.
 *
 * Not a plugin, and not tied to one. The web's version owns a WebSocket
 * because a browser has nothing else; a native app is already holding a
 * connection to this server and opening a second one to hear the same numbers
 * would be two sockets disagreeing about one job.
 */
public class TranscodeGate(
    private val seekTimeoutMs: Long = DEFAULT_SEEK_TIMEOUT_MS,
) {
    private var writeHeadSeconds: Double = 0.0
    private var jobId: String? = null

    /** How far the encoder has got, in seconds. */
    public fun transcodedTo(): Double = writeHeadSeconds

    public fun currentJob(): String? = jobId

    /** A new item resets the head: the next job has encoded nothing yet. */
    public fun startItem(id: String) {
        jobId = id
        writeHeadSeconds = 0.0
    }

    /** Applies a status message and returns it parsed, for a caller that logs. */
    public fun accept(message: TranscodeMessage): TranscodeMessage {
        when (message) {
            is TranscodeMessage.Started -> jobId = message.jobId
            is TranscodeMessage.Progress -> {
                jobId = message.progress.jobId.ifEmpty { jobId.orEmpty() }
                // Never backwards. A late-arriving progress message from
                // before a seek would otherwise move the head back and gate a
                // position the server has already passed.
                writeHeadSeconds = maxOf(writeHeadSeconds, message.progress.transcodedSeconds)
            }
            else -> Unit
        }
        return message
    }

    /**
     * Whether a seek to [targetSeconds] can go ahead.
     *
     * [waitedMs] is how long the caller has already waited, so the decision
     * stays a pure function of state and the caller owns the clock — a gate
     * that read a clock itself could not be tested without one.
     */
    public fun verdictFor(targetSeconds: Double, waitedMs: Long = 0): SeekVerdict = when {
        targetSeconds <= writeHeadSeconds -> SeekVerdict.Ready
        waitedMs >= seekTimeoutMs -> SeekVerdict.TimedOut
        else -> SeekVerdict.Waiting
    }
}

/**
 * Parses one status message.
 *
 * Separate from the gate so a caller on any transport can use it, and so a
 * malformed message is a value rather than an exception: a server sending
 * something unparseable should not take the player down with it.
 */
public fun parseTranscodeMessage(
    type: String?,
    jobId: String? = null,
    sourceUrl: String? = null,
    transcodedSeconds: Double? = null,
    totalSeconds: Double? = null,
    variantsReady: List<String> = emptyList(),
): TranscodeMessage? = when (type) {
    "started" -> TranscodeMessage.Started(jobId.orEmpty(), sourceUrl.orEmpty())

    // A progress message with no number carries nothing, which is the web's
    // `typeof msg.transcodedSeconds === 'number'` check. Treating it as zero
    // would move the head backwards.
    "progress" -> transcodedSeconds?.let {
        TranscodeMessage.Progress(
            TranscodeProgress(jobId.orEmpty(), it, totalSeconds, variantsReady),
        )
    }

    "ready-to-play" -> TranscodeMessage.ReadyToPlay(jobId.orEmpty())
    "complete" -> TranscodeMessage.Complete(jobId.orEmpty())
    null -> null
    else -> TranscodeMessage.Unknown(type)
}

/** The web's `seekTimeoutMs ?? 10_000`. */
public const val DEFAULT_SEEK_TIMEOUT_MS: Long = 10_000
