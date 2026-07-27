// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.flow.StateFlow

// Finding NoMercy televisions on the network.
//
// Three implementations of one idea — Android has its own name-service manager,
// Apple a network browser, the desktop a Java mDNS library — over the same
// service type and the same records, because a house with a phone and a laptop
// in it has to see the same list on both.
public interface DeviceDiscovery {

    // A flow rather than a callback: sets appear and disappear as they sleep and
    // wake, and a picker showing a list has to follow that rather than sample it
    // once when it opened.
    public val devices: StateFlow<List<RemoteDevice>>

    public fun start()

    // Stopped explicitly, because a browse left running is a radio kept awake.
    // On a phone that is measurable battery for a list nobody is looking at.
    public fun stop()

    public companion object {
        public const val SERVICE_TYPE: String = "_nomercy._tcp"
    }
}
