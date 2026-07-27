// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.device.DeviceCapabilities
import tv.nomercy.player.core.device.FormFactor

// What a form factor implies, for a caller that only named one.
//
// The real answers come from the platform, and a host that has them passes them
// down. This is for the chrome asked to render as a phone or a desktop without
// being told anything else: the key handler needs capabilities to decide what
// the arrow keys do, and guessing them at that call site would put the same
// guess in three places.
//
// Every field here follows from the form factor and nothing else. Anything that
// does not — a phone with a gamepad attached, a set-top box driven from an app —
// is exactly why the platform answer exists and why this is not it.
internal data class ChromeCapabilities(
    override val formFactor: FormFactor,
) : DeviceCapabilities {

    override val hasDpad: Boolean get() = formFactor == FormFactor.Tv

    override val hasTouch: Boolean get() = formFactor == FormFactor.Phone || formFactor == FormFactor.Tablet

    override val hasPointer: Boolean get() = formFactor == FormFactor.Desktop

    // A television's volume keys talk to the panel or the receiver, and a
    // desktop has none at all. A handheld is the one that has them and delivers
    // them to whatever is in front.
    override val hasHardwareVolumeKeys: Boolean get() = hasTouch
}
