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

    public actual fun whyUnavailable(): String? {
        loaded()
        return loadFailure
    }

    private var loadFailure: String? = null

    // Loaded once and remembered. JNA reports a missing library by throwing from
    // the load itself, and letting that escape a question like "is this
    // available" would make the check the crash it exists to prevent.
    private val instance: LibAss? by lazy {
        try {
            Native.load("ass", LibAss::class.java)
        } catch (missing: UnsatisfiedLinkError) {
            loadFailure = "libass is not installed on this machine: ${missing.message}"
            null
        } catch (unloadable: LinkageError) {
            loadFailure = "libass could not be loaded: ${unloadable.message}"
            null
        } catch (refused: RuntimeException) {
            loadFailure = "libass refused to load: ${refused.message}"
            null
        }
    }

    private fun loaded(): LibAss? = instance
}
