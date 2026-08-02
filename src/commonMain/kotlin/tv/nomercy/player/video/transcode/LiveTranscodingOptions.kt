// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.transcode

// Where the encoder's job lifecycle lives, and how long to wait on it.
//
// The web spells the endpoint `controlUrl` with a `wsUrl` alias, because its
// first version shipped the alias and renaming it would have broken consumers.
// Native has no such history and takes the one name, which is the rename rule
// working the way it is supposed to: a field carrying the data needs no
// duplicate.
public data class LiveTranscodingOptions(
    /** The server that owns the job, typically a WebSocket. Null leaves the plugin inert. */
    val controlUrl: String? = null,

    /** How long a seek waits for the encoder to reach it before going through anyway. */
    val seekTimeoutMs: Long = DEFAULT_SEEK_TIMEOUT_MS,

    /** How many seconds beyond the position the encoder should stay ahead. */
    val resumeAheadSeconds: Double = DEFAULT_RESUME_AHEAD_SECONDS,

    /** The rung to ask the encoder for. Null lets the server decide. */
    val preferredHeight: Int? = null,
)

private const val DEFAULT_SEEK_TIMEOUT_MS = 10_000L
private const val DEFAULT_RESUME_AHEAD_SECONDS = 5.0
