// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import android.content.Context

public actual class AssPlatformContext(public val android: Context)

public actual object AssRenderers {

    public actual fun create(context: AssPlatformContext): AssRenderer? =
        if (whyUnavailable() == null) AndroidAssRenderer() else null

    // The binding ships its own libass for every Android ABI, so the only way it
    // is missing is a build that stripped it. Asking the classloader is the only
    // honest answer — a hardcoded "available" would turn a packaging mistake
    // into blank subtitles with no explanation.
    // LinkageError, not just UnsatisfiedLinkError. The binding loads its native
    // library in a static initializer, so on a host JVM — a Robolectric run, a
    // unit test, an Android build missing this ABI — the class is found and then
    // fails to initialize, which arrives as NoClassDefFoundError. Catching only
    // the narrower type made this function throw the very failure it exists to
    // report.
    @Suppress("TooGenericExceptionCaught")
    public actual fun whyUnavailable(): String? = try {
        Class.forName("io.github.peerless2012.ass.Ass")
        null
    } catch (missing: ClassNotFoundException) {
        "libass binding not on the classpath: ${missing.message}"
    } catch (unlinkable: LinkageError) {
        "libass native library did not load on this platform: ${unlinkable.message}"
    } catch (refused: RuntimeException) {
        "libass binding refused to initialize: ${refused.message}"
    }
}
