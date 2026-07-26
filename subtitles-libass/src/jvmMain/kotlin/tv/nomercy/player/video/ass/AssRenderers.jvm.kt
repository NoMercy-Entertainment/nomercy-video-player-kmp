// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import com.sun.jna.Native

public actual class AssPlatformContext

// libass on the desktop, when the machine has it.
//
// It is a system package on Linux and a Homebrew formula on macOS, and this
// loads whichever is installed. On Windows the only builds in circulation are
// the copies statically linked inside VLC and mpv, which cannot be loaded from
// outside them — so Windows gets the sentence rather than the renderer until a
// vendored build is decided on, and that is a distribution decision rather than
// a rendering one.
//
// Asked rather than assumed, because "styled subtitles or plain text" is a
// choice the caller makes, and a developer looking at blank subtitles needs a
// sentence instead of a stack trace.
public actual object AssRenderers {

    public actual fun create(context: AssPlatformContext): AssRenderer? {
        val lib: LibAss = loaded() ?: return null
        val library = lib.ass_library_init() ?: return null
        return JvmAssRenderer(lib, library)
    }

    public actual fun whyUnavailable(): String? = if (loaded() != null) null else loadFailure

    private var loadFailure: String? = null

    // Loaded once and remembered. JNA reports a missing library by throwing from
    // the load itself, and letting that escape a question like "is this
    // available" would make the check the crash it exists to prevent.
    private val instance: LibAss? by lazy { candidates().firstNotNullOfOrNull(::attempt) }

    // By name first, then by the paths package managers actually use.
    //
    // Homebrew installs into /opt/homebrew on Apple silicon and /usr/local on
    // Intel, and a JVM searches neither: the default dyld path does not include
    // them, so a machine with libass installed reports it missing. Naming them
    // is less elegant than requiring DYLD_LIBRARY_PATH and it is the difference
    // between working on a developer's machine and not.
    private fun candidates(): List<String> = listOf(
        "ass",
        "/opt/homebrew/lib/libass.dylib",
        "/usr/local/lib/libass.dylib",
        "/usr/lib/x86_64-linux-gnu/libass.so.9",
        "/usr/lib64/libass.so.9",
    )

    @Suppress("TooGenericExceptionCaught")
    private fun attempt(location: String): LibAss? = try {
        Native.load(location, LibAss::class.java)
    } catch (missing: UnsatisfiedLinkError) {
        recordFailure("libass is not installed on this machine", missing.message)
        null
    } catch (unloadable: LinkageError) {
        recordFailure("libass could not be loaded", unloadable.message)
        null
    } catch (refused: RuntimeException) {
        recordFailure("libass refused to load", refused.message)
        null
    }

    // The first failure is the useful one — it names the plain lookup rather
    // than the last hardcoded path, which is what a reader wants to see.
    private fun recordFailure(what: String, detail: String?) {
        if (loadFailure == null) loadFailure = "$what: $detail"
    }

    private fun loaded(): LibAss? = instance
}
