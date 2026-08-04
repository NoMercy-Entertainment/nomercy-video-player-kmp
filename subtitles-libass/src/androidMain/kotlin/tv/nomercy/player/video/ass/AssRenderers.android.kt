// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import android.app.ActivityManager
import android.content.Context
import com.sun.jna.Native
import tv.nomercy.player.video.ass.render.MemoryTier
import tv.nomercy.player.video.subtitles.AssRenderer

public actual class AssPlatformContext(public val android: Context)

// libass on Android: OUR build, through the same JNA binding the desktop uses.
//
// This was io.github.peerless2012:ass-kt — a second driver for a library the
// desktop already drove through LibAss. Two drivers is two places for a cache
// limit, a frame size or a font registration to be set differently, which is
// how the same subtitle renders correctly on one surface and wrong on another
// with nothing reporting it.
//
// The .so comes from nomercy-libass, built from the same pinned sources as
// every other platform, so a sign drawn on a phone and one drawn in a browser
// came out of the same code.
public actual object AssRenderers {

    public actual fun create(context: AssPlatformContext): AssRenderer? {
        val lib: LibAss = loaded() ?: return null
        val library = lib.ass_library_init() ?: return null

        val tier: MemoryTier = tierFor(context.android)

        return NativeAssRenderer(lib, library, tier.glyphCacheMax, tier.libassCacheMegabytes)
    }

    public actual fun whyUnavailable(): String? = if (loaded() != null) null else loadFailure

    private var loadFailure: String? = null

    // Loaded once and remembered. JNA reports a missing library by throwing from
    // the load itself, and letting that escape a question like "is this
    // available" would make the check the crash it exists to prevent.
    private val instance: LibAss? by lazy { attempt() }

    private fun loaded(): LibAss? = instance

    // The heap the system granted this process, which is the only honest input
    // to the budget. A television box and a flagship phone run the same build
    // and cannot afford the same subtitle cache.
    private fun tierFor(context: Context): MemoryTier {
        val manager: ActivityManager? =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        return MemoryTier.forHeapMegabytes(manager?.memoryClass ?: 0)
    }

    // Plain "ass": the packaged library is `libass.so` in this build's jniLibs
    // for the running ABI, and JNA adds the prefix and suffix itself. A path
    // here would be a path that only exists on the machine that wrote it.
    @Suppress("TooGenericExceptionCaught")
    private fun attempt(): LibAss? = try {
        Native.load("ass", LibAss::class.java)
    } catch (missing: UnsatisfiedLinkError) {
        recordFailure("libass is not in this build's jniLibs", missing.message)
        null
    } catch (unloadable: LinkageError) {
        recordFailure("libass could not be loaded", unloadable.message)
        null
    } catch (refused: RuntimeException) {
        recordFailure("libass refused to load", refused.message)
        null
    }

    private fun recordFailure(what: String, detail: String?) {
        if (loadFailure == null) loadFailure = "$what: $detail"
    }
}
