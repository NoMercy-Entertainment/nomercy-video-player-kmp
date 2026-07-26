// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

public actual class AssPlatformContext

// The desktop has no libass binding yet, and this says so rather than crashing
// somewhere else.
//
// The gap is a binary, not a design. libass ships as a system package on Linux
// and through Homebrew on macOS, and on Windows the only builds in circulation
// are the copies statically linked inside VLC and mpv — which are not loadable
// from outside them. Binding it means either vendoring a build per platform or
// depending on one being installed, and that decision is not one to take by
// accident inside a subtitle renderer.
//
// Until then a desktop caller asks, gets a sentence, and can fall back to plain
// text. That is a worse subtitle, not a broken player.
public actual object AssRenderers {

    public actual fun create(context: AssPlatformContext): AssRenderer? = null

    public actual fun whyUnavailable(): String? =
        "no libass binding on the JVM yet: a native build has to be vendored or required per platform"
}
