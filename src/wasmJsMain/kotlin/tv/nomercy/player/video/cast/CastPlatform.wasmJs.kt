// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

// cast-web IS the Cast receiver — it has no panel of its own to wake and no
// need to discover other televisions on the network, same "nothing to do"
// shape jvmMain's desktop actual already declares.
public actual fun defaultCastWaker(): CastWaker = UnsupportedCastWaker()

public actual fun defaultDeviceDiscovery(): DeviceDiscovery = NoDeviceDiscovery()
