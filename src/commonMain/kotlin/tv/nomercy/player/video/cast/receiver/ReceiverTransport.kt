// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

// How a decoded ReceiverCommand reaches ReceiverSession, and how the outcome
// gets back to whichever sender asked. P22b decided both, behind this one
// seam: Cast for a sender that is not ours, a NoMercy-protocol transport
// (Ktor WebSocket on Android, mirroring TvControlServer's session/transport
// routes) for a sender that is. Neither implementation may hold protocol
// logic — a rule that cannot live in ReceiverSession is a sign this seam is
// drawn in the wrong place.
public interface ReceiverTransport {

    // Start listening. commandHandler is called once per decoded command with
    // the sender that sent it; its return value is what the transport reports
    // back over its own wire (a WebSocket frame, a Cast reply — the transport
    // decides the shape, never this interface).
    public fun start(commandHandler: (senderId: String, command: ReceiverCommand) -> ReceiverOutcome)

    public fun stop()

    // A sender's connection ended without an explicit Stop — the transport
    // noticed (socket closed, Cast session ended) and reports it so the
    // session can release whoever it was holding for. Mirrors
    // ReceiverSession.release; called by the transport, never assumed by it.
    public fun onDisconnect(handler: (senderId: String) -> Unit)
}

// No transport is listening — the receiver has not been started, or exists
// only to satisfy a target this build has no real transport for yet (tvOS
// AirPlay-only paths, a desktop target that never hosts a receiver).
public object NoReceiverTransport : ReceiverTransport {
    override fun start(commandHandler: (senderId: String, command: ReceiverCommand) -> ReceiverOutcome) {
        // Deliberately inert: nothing to start, so nothing to answer.
    }

    override fun stop() {
        // Nothing running.
    }

    override fun onDisconnect(handler: (senderId: String) -> Unit) {
        // No connections to lose.
    }
}
