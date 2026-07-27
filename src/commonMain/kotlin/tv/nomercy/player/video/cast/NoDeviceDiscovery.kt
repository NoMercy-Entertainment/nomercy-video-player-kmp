// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// A platform whose network browser has not been written yet.
//
// An empty list rather than an exception, and rather than a picker that cannot
// open. A viewer on such a platform can still cast by being handed an address
// some other way; what they cannot do is browse, and that is the accurate
// statement of where this stands.
public open class NoDeviceDiscovery : DeviceDiscovery {

    private val nothing = MutableStateFlow<List<RemoteDevice>>(emptyList())

    override val devices: StateFlow<List<RemoteDevice>> = nothing.asStateFlow()

    override fun start(): Unit = Unit

    override fun stop(): Unit = Unit
}
