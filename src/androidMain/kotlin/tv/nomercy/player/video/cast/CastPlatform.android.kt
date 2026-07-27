// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import tv.nomercy.player.core.ports.PlatformEnvironment

// Waking a panel through a Chromecast is not built yet, and it is the one part
// of casting that needs hardware to prove: a receiver application id, a real
// Chromecast on the network, and a television that is actually asleep. Until it
// exists, an Android caller is told the wake is unsupported rather than being
// told it succeeded — a set that never woke and a phone that thinks it did is
// the worst of the available answers.
public actual fun defaultCastWaker(): CastWaker = UnsupportedCastWaker()

// Discovery is built, and falls back to nothing when the library has not been
// given a context. That happens in a host test, and a browse is not a reason for
// a player to refuse to start.
public actual fun defaultDeviceDiscovery(): DeviceDiscovery =
    if (PlatformEnvironment.isInstalled()) {
        NsdDeviceDiscovery(PlatformEnvironment.requireContext().androidContext)
    } else {
        NoDeviceDiscovery()
    }
