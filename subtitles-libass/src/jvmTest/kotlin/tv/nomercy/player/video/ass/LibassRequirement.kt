// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlin.test.fail

// Whether a missing libass is allowed to end a gate quietly.
//
// A skip is right on a developer's Windows machine, where the only builds in
// circulation are the copies statically linked inside VLC and mpv and cannot be
// loaded from outside them. It is exactly wrong on the Linux job, which installs
// the package on purpose: there, a skip means the install stopped working and
// the whole desktop rendering path went untested behind a green tick.
//
// So the job that installs it says so, and the gate fails rather than skips.
internal object LibassRequirement {

    private const val VARIABLE = "NOMERCY_REQUIRE_LIBASS"

    // The renderer, or null if this machine is allowed not to have one.
    //
    // Never returns null where the library was required — it fails first, with
    // the reason libass itself gave, because "not installed" and "installed but
    // the wrong ABI" need different fixes and the message is the only thing that
    // tells them apart.
    fun rendererOrSkip(): AssRenderer? {
        val reason: String? = AssRenderers.whyUnavailable()
        if (reason == null) return AssRenderers.create(AssPlatformContext())

        if (isRequired()) {
            fail("libass was required on this host and is not usable: $reason")
        }

        println("skipped: $reason")
        return null
    }

    fun isRequired(): Boolean = System.getenv(VARIABLE) == "1"
}
