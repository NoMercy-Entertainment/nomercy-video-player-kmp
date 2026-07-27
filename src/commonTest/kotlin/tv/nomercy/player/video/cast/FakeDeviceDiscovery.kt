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

// A network with whatever televisions a test says are on it.
class FakeDeviceDiscovery : DeviceDiscovery {

    private val found = MutableStateFlow<List<RemoteDevice>>(emptyList())

    override val devices: StateFlow<List<RemoteDevice>> = found.asStateFlow()

    var started: Boolean = false
        private set

    var stopped: Boolean = false
        private set

    override fun start() {
        started = true
    }

    override fun stop() {
        stopped = true
    }

    // A set appearing or disappearing, which is what browsing a network is.
    fun emit(devices: List<RemoteDevice>) {
        found.value = devices
    }
}
