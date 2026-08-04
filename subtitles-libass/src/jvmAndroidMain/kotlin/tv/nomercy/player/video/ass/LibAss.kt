// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.Structure

// libass's C API, only the calls this renderer makes.
//
// Hand-declared rather than generated because the surface is a dozen functions
// and one struct, and a generator would bring a build step and a toolchain for
// something that has not changed shape in a decade.
//
// Every pointer is opaque. The structs behind ASS_Library, ASS_Renderer and
// ASS_Track are private to libass, and reaching into them is how a binding
// breaks on a point release.
@Suppress("FunctionNaming", "FunctionParameterNaming", "LongParameterList")
internal interface LibAss : Library {

    fun ass_library_init(): Pointer?
    fun ass_library_done(library: Pointer)

    // name is a label for the embedded blob. libass matches a style's font
    // against the family recorded inside the data, not against this.
    fun ass_add_font(library: Pointer, name: String, data: ByteArray, dataSize: Int)

    fun ass_renderer_init(library: Pointer): Pointer?
    fun ass_renderer_done(renderer: Pointer)

    fun ass_set_frame_size(renderer: Pointer, w: Int, h: Int)
    fun ass_set_storage_size(renderer: Pointer, w: Int, h: Int)

    // Without this call libass has no font provider and renders nothing. It is
    // not optional and it is easy to miss, because every other call succeeds.
    fun ass_set_fonts(
        renderer: Pointer,
        defaultFont: String?,
        defaultFamily: String?,
        defaultFontProvider: Int,
        config: String?,
        update: Int,
    )

    // The same two limits Android's tiers set. libass defaults to 128MB of
    // bitmap cache, which is a number chosen for a desktop with headroom and
    // ruinous anywhere else — and the desktop drop-in ends up inside consumer
    // applications with budgets of their own.
    //
    // bitmapMaxMegabytes is megabytes, not bytes. Passing bytes asks for a cache
    // a million times the intended size, which libass accepts.
    fun ass_set_cache_limits(renderer: Pointer, glyphMax: Int, bitmapMaxMegabytes: Int)

    // Drops every font added to the library. A queue advancing to a title with
    // its own attached fonts otherwise resolves against the previous one's, and
    // that failure is silent: the cue renders in a face that exists rather than
    // the one the disc carried.
    fun ass_clear_fonts(library: Pointer)

    fun ass_read_memory(library: Pointer, buf: ByteArray, bufSize: Int, codepage: String?): Pointer?
    fun ass_free_track(track: Pointer)

    fun ass_render_frame(renderer: Pointer, track: Pointer, now: Long, detectChange: IntArray?): Pointer?
}

// The head of a linked list of rasterized runs, in libass's own field order.
//
// Field order is the contract with C, and getting it wrong reads whatever bytes
// happen to be there — a subtitle drawn at a nonsense position rather than a
// crash, which is far harder to notice.
@Structure.FieldOrder("w", "h", "stride", "bitmap", "color", "dstX", "dstY", "next", "type")
internal open class AssImageStruct(pointer: Pointer? = null) : Structure(pointer) {
    @JvmField var w: Int = 0

    @JvmField var h: Int = 0

    @JvmField var stride: Int = 0

    // 8-bit coverage, one byte per pixel, `stride` wide. Not RGBA: the colour is
    // a separate field the surface multiplies in.
    @JvmField var bitmap: Pointer? = null

    @JvmField var color: Int = 0

    @JvmField var dstX: Int = 0

    @JvmField var dstY: Int = 0

    @JvmField var next: Pointer? = null

    @JvmField var type: Int = 0

    init {
        if (pointer != null) read()
    }
}

// AUTODETECT, which is CoreText on macOS and fontconfig on Linux. Both find the
// system's fonts, which is what a subtitle naming a common family needs.
internal const val FONT_PROVIDER_AUTODETECT: Int = 1
