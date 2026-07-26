// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

public actual class AssPlatformContext

// Apple has a libass build in the ecosystem already — nomercy-app-kmp vendors
// one and builds it with apple/Vendor/libass-build/build-ios.sh — but it is not
// wired to this module yet.
//
// Wiring it means a cinterop def against the built framework's headers and a
// static library per slice, which makes this module's Apple targets depend on an
// artifact no CI runner produces today. That is a build-graph decision rather
// than a renderer decision, so it waits for the plan that makes it, and until
// then an Apple caller gets a sentence instead of a symbol it cannot link.
public actual object AssRenderers {

    public actual fun create(context: AssPlatformContext): AssRenderer? = null

    public actual fun whyUnavailable(): String? =
        "libass is vendored for Apple in nomercy-app-kmp but not yet linked into this module"
}
