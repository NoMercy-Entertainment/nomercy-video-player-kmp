// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tv.nomercy.player.core.device.DeviceCapabilities
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.device.platformDeviceCapabilities
import tv.nomercy.player.core.ports.PlatformEnvironment

// What this device is, for a view that was not told.
//
// Asked of the platform, which is the whole point of the capability contract:
// the two clients this replaces each sniffed a user agent or read three Android
// signals of their own, and that is why the same tablet was a phone in one and a
// television in the other.
//
// The fallback is a desktop rather than a guess at the real answer. A platform
// with nothing installed is one that never had a context to install — a Compose
// Desktop build — and a library that claimed to have detected something there
// would be inventing a measurement.
@Composable
public fun rememberDeviceCapabilities(): DeviceCapabilities = remember {
    if (PlatformEnvironment.isInstalled()) {
        platformDeviceCapabilities(PlatformEnvironment.requireContext())
    } else {
        DesktopFallback
    }
}

private object DesktopFallback : DeviceCapabilities {
    override val formFactor: FormFactor = FormFactor.Desktop
    override val hasDpad: Boolean = false
    override val hasTouch: Boolean = false
    override val hasPointer: Boolean = true
    override val hasHardwareVolumeKeys: Boolean = false
}
