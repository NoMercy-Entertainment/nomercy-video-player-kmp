// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.video.subtitles.AssRenderer

// Whether this platform can draw styled subtitles yet, and why not when it
// cannot.
//
// A missing renderer is a fact about a native library on a platform, not a
// programming error, so it is answered rather than thrown. The shape mirrors
// VlcjVideoBackend.isAvailable/whyUnavailable in core for the same reason: a
// caller that can degrade to plain text needs to ask, and a developer staring at
// blank subtitles needs a sentence rather than a stack trace.
//
// It is expect/actual rather than a runtime lookup so that the gaps are visible
// in the source of each platform instead of hidden behind a table.
public expect object AssRenderers {

    // Null when this platform has no libass binding yet — the reason says which
    // platform and what is missing.
    public fun create(context: AssPlatformContext): AssRenderer?

    public fun whyUnavailable(): String?
}

public fun AssRenderers.isAvailable(): Boolean = whyUnavailable() == null

// Whatever the platform needs in order to load a native library and find its
// fonts. Nothing on the desktop and on Apple; an Android Context on Android.
public expect class AssPlatformContext
