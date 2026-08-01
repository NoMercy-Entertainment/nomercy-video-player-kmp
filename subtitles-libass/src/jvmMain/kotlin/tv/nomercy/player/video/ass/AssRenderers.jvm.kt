// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import com.sun.jna.Native
import java.io.File
import tv.nomercy.player.core.natives.NativeRuntimeKind
import tv.nomercy.player.core.natives.NativeRuntimes

public actual class AssPlatformContext

// libass on the desktop: the copy that came with the library, or failing that
// whichever one the machine has.
//
// The payload is the same arrangement the Apple targets already use — a
// prebuilt archive, pinned by digest, fetched rather than compiled — and it is
// resolved through the shared loader in core, so libVLC and libass arrive the
// same way rather than through two mechanisms nobody can hold in their head.
//
// A system libass is still accepted underneath it. On Linux it is a package
// most distributions already have, and there is no reason to download one over
// it.
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

    // The payload first, the machine second, and that order is the fix.
    //
    // It used to be the machine only, which meant styled subtitles rendered on
    // a developer's laptop with Homebrew and silently did not on a user's — the
    // same defect the desktop engine had when it hunted for an installed VLC.
    // A library whose features depend on what somebody happened to install is a
    // library that behaves differently for every user.
    //
    // The system paths stay underneath, because a distribution that already
    // ships libass should not be made to download one, and because a payload
    // has not been published for every platform yet. Homebrew installs into
    // /opt/homebrew on Apple silicon and /usr/local on Intel, and a JVM
    // searches neither.
    private fun candidates(): List<String> = fromPayload() + listOf(
        "ass",
        "/opt/homebrew/lib/libass.dylib",
        "/usr/local/lib/libass.dylib",
        "/usr/lib/x86_64-linux-gnu/libass.so.9",
        "/usr/lib64/libass.so.9",
    )

    // The copy that arrived through the dependency, if one did. Empty when no
    // payload has been published for this platform, which is not an error — the
    // list below is what happens next.
    private fun fromPayload(): List<String> {
        val directory: File = NativeRuntimes.directory(NativeRuntimeKind.LIB_ASS) ?: return emptyList()
        return listOf("libass-9.dll", "libass.so.9", "libass.dylib")
            .map { name -> File(directory, name) }
            .filter { candidate -> candidate.isFile }
            .map { candidate -> candidate.absolutePath }
    }

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
