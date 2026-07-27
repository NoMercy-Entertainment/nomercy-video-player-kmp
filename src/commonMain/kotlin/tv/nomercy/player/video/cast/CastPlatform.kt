// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

// What this platform can do about finding and waking televisions.
//
// Two factories rather than one object, because the two answers are independent:
// every platform can find a set on the network, and only Android can wake a
// panel through a Chromecast.
public expect fun defaultCastWaker(): CastWaker

public expect fun defaultDeviceDiscovery(): DeviceDiscovery

// Whether a route the system offers is one a television might be behind.
//
// Extracted from the Android waker so it can be asserted without a device. The
// two exclusions are not arbitrary: the default route is this phone's own
// speaker, and a bluetooth route is a headset. Selecting either would move audio
// somewhere the viewer did not ask for, which on the default route means
// casting a film to the phone that is trying to cast it.
internal fun isSelectableCastRoute(isDefault: Boolean, isBluetooth: Boolean): Boolean =
    !isDefault && !isBluetooth
