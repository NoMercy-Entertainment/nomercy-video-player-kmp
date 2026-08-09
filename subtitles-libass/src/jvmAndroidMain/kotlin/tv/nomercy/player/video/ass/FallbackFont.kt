// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import java.io.File

/**
 * The face libass falls back to when the system offers nothing.
 *
 * Carried rather than relied upon. libass resolves a cue's font through a system
 * provider, and on a machine where that provider answers nothing EVERY cue
 * renders as no glyphs at all — silently, with the layout done and the timing
 * right and the screen empty. Measured on Ubuntu 24.04 with libass 0.17.5 linked
 * against fontconfig, 24 fonts installed and `fc-match "Skeleton Sans"`
 * resolving to DejaVu at the shell: libass still reported
 * `fontselect: failed to find any fallback with glyph 0x0`.
 *
 * A file, not bytes. `ass_add_font` registers a face for a script that NAMES it;
 * it does not make one the fallback — probed, and a face registered in memory
 * under the cue's own family still drew nothing. `ass_set_fonts` takes the
 * fallback as a PATH, so the resource is unpacked once and the path handed over.
 *
 * Roboto, under the Apache Licence 2.0, which is the same licence this library
 * ships under.
 */
internal object FallbackFont {

    private const val RESOURCE = "/tv/nomercy/player/video/ass/NoMercyFallback.ttf"

    /**
     * The path to the unpacked face, or null when it could not be written.
     *
     * Null is a real answer rather than a failure: a machine whose temp
     * directory refuses the write still has whatever system fonts it has, and
     * taking the renderer down over a fallback would be worse than the gap the
     * fallback exists to close.
     */
    val path: String? by lazy { unpack()?.absolutePath }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun unpack(): File? = try {
        val bytes: ByteArray = FallbackFont::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes() }
            ?: return null

        // Named for its content length as well as its name, so a library
        // upgrade that changes the face does not keep serving the old one out
        // of a directory that outlives the process.
        val file = File(System.getProperty("java.io.tmpdir"), "nomercy-ass-fallback-${bytes.size}.ttf")
        if (!file.isFile || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
        file
    } catch (unavailable: RuntimeException) {
        null
    } catch (refused: java.io.IOException) {
        null
    }
}
