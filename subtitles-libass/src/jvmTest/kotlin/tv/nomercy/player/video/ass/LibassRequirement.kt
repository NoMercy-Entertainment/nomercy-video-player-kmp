// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.core.natives.NativeRuntimeKind
import tv.nomercy.player.core.natives.NativeRuntimes
import tv.nomercy.player.video.subtitles.AssRenderer
import kotlin.test.fail

// Whether a missing libass is allowed to end a gate quietly.
//
// This used to skip on Windows, on the premise that "the only builds in
// circulation are the copies statically linked inside VLC and mpv". That
// premise died when nomercy-libass started publishing a windows-x64 archive,
// and nobody told the gate — so the whole styled-subtitle path went untested on
// the desktop behind five green ticks, and the way it surfaced instead was a
// viewer saying an episode had no subtitles.
//
// A gate that skips on the platform where the thing is broken is not a gate.
// So the question is no longer "which host is this" but "is a payload
// published for this host": where one is, libass is required and a failure to
// load it is a red, whatever the reason. That is the same question the shipped
// loader asks, which is the point — the gate and the product now disagree about
// nothing.
//
// The environment variable stays as an override for a host with no published
// payload, so the Linux job that installs the distribution package still says
// so and still fails rather than skips.
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

    fun isRequired(): Boolean =
        System.getenv(VARIABLE) == "1" || NativeRuntimes.isPublished(NativeRuntimeKind.LIB_ASS)
}
