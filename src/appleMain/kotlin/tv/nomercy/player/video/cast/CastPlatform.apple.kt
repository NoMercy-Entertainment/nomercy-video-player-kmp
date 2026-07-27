// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

// Apple reaches a television directly over its control protocol and has no panel
// to wake, so there is nothing here to build.
public actual fun defaultCastWaker(): CastWaker = UnsupportedCastWaker()

// Not built yet. A browser over the same service type is the remaining work, and
// until it exists a picker on iOS is handed an empty list rather than a crash.
public actual fun defaultDeviceDiscovery(): DeviceDiscovery = NoDeviceDiscovery()
