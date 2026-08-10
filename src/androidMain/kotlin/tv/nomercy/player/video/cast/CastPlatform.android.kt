// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import tv.nomercy.player.core.ports.PlatformEnvironment

// Waking a panel through a Chromecast — the `PhoneCastSenderImpl` port
// (`ChromecastCastWaker`) when the library has a context to hold a multicast
// lock and drive `MediaRouter` with, `UnsupportedCastWaker` otherwise. That
// happens in a host test, and a browse is not a reason for a player to refuse
// to start.
public actual fun defaultCastWaker(): CastWaker =
    if (PlatformEnvironment.isInstalled()) {
        ChromecastCastWaker(PlatformEnvironment.requireContext().androidContext)
    } else {
        UnsupportedCastWaker()
    }

// Discovery is built, and falls back to nothing when the library has not been
// given a context. That happens in a host test, and a browse is not a reason for
// a player to refuse to start.
public actual fun defaultDeviceDiscovery(): DeviceDiscovery =
    if (PlatformEnvironment.isInstalled()) {
        NsdDeviceDiscovery(PlatformEnvironment.requireContext().androidContext)
    } else {
        NoDeviceDiscovery()
    }
