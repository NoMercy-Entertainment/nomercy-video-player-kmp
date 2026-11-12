// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

// What a sender presents when it opens a receiver socket. The bearer is the
// Authorization header token, else the access_token query parameter.
public data class ReceiverHandshake(
    val senderId: String,
    val bearer: String?,
    val remoteHost: String,
)

// The host passes a policy that validates the bearer (the NoMercy app uses the
// owner-or-guest rule of its TvControlServer). Without one, every sender is refused.
public fun interface ReceiverAuthPolicy {
    public fun authorize(handshake: ReceiverHandshake): Boolean

    public companion object {
        public val DenyAll: ReceiverAuthPolicy = ReceiverAuthPolicy { false }
    }
}
