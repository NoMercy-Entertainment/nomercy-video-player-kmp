// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// What a television says about itself before anyone has authenticated.
//
// Named for the thing rather than for the shape of it: this is the server, not
// information about one.
//
// Deliberately answerable without a credential: a picker has to be able to list
// a set it has never talked to, and asking for a token first would mean a device
// that is invisible until it is already trusted.
//
// Capabilities are strings rather than an enum, and that is the compatibility
// choice: a set advertising something this build has never heard of should be
// listed rather than rejected, and the list is the set's vocabulary rather than
// the phone's.
@Serializable
public data class RemoteServer(
    @SerialName("server_name")
    val serverName: String = "",
    val version: String = "",
    val capabilities: List<String> = emptyList(),
)
