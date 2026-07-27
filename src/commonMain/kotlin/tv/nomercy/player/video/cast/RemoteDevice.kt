// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

// A NoMercy television found on the network.
public data class RemoteDevice(
    val id: String,
    val serviceName: String,
    val host: String,
    val port: Int,
    val loginState: String = "",
    val fingerprint: String = "",
) {

    // Without the digits the set appends to make its name unique on the
    // network. Those exist so two televisions in one house do not collide, and
    // showing them to a viewer picking between rooms is showing plumbing.
    public val displayName: String get() = serviceName.replace(UNIQUE_SUFFIX, "")

    // A set nobody has signed into yet. It is still worth listing — that is how
    // a viewer sets one up — but casting to it will not work until someone has,
    // and a picker that hides it leaves them with no way in.
    public val needsSignIn: Boolean get() = loginState == UNAUTHENTICATED

    private companion object {
        val UNIQUE_SUFFIX = Regex("""-\d{5}$""")
        const val UNAUTHENTICATED = "unauthenticated"
    }
}
