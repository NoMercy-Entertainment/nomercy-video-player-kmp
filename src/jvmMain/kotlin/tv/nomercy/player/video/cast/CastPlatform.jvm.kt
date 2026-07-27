// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

// A desktop reaches a television directly and has no panel to wake.
public actual fun defaultCastWaker(): CastWaker = UnsupportedCastWaker()

// Not built yet. The desktop needs an mDNS library of its own, which is a
// dependency decision rather than a line of code.
public actual fun defaultDeviceDiscovery(): DeviceDiscovery = NoDeviceDiscovery()
