// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import libass.ASS_Image
import libass.ASS_Library
import libass.ASS_Renderer
import libass.ASS_Track
import libass.ass_add_font
import libass.ass_clear_fonts
import libass.ass_free_track
import libass.ass_library_done
import libass.ass_library_init
import libass.ass_read_memory
import libass.ass_render_frame
import libass.ass_renderer_done
import libass.ass_renderer_init
import libass.ass_set_cache_limits
import libass.ass_set_fonts
import libass.ass_set_frame_size
import libass.ass_set_storage_size

// libass on Apple, over Kotlin/Native cinterop.
//
// The third binding to the same C library and the third time the same two rules
// apply: the renderer is built lazily so fonts added later are not ignored, and
// the image list is copied out because libass owns it until the next render.
// Writing that three ways in three languages is what the shared contract is for.
//
// Font resolution is CoreText — the build drops fontconfig, which is the right
// call on a platform that has a system font database of its own.
@OptIn(ExperimentalForeignApi::class)
internal class AppleAssRenderer(private val library: CPointer<ASS_Library>) : AssRenderer {

    private var renderer: CPointer<ASS_Renderer>? = null
    private var track: CPointer<ASS_Track>? = null
    private var trackContent: String? = null
    private var width: Int = 0
    private var height: Int = 0
    private var released: Boolean = false

    override fun addFont(name: String, data: ByteArray) {
        if (released || data.isEmpty()) return
        memScoped {
            ass_add_font(library, name.cstr.ptr, data.refTo(0), data.size)
        }
        disposeRenderer()
    }

    // Drops every font the library holds. A queue advancing to a title with
    // its own attached fonts otherwise resolves against the previous one's,
    // and that failure is silent: the cue renders in a face that exists
    // rather than the one the disc carried.
    override fun clearFonts() {
        if (released) return
        ass_clear_fonts(library)
        disposeRenderer()
    }

    override fun loadTrack(assContent: String) {
        if (released) return
        trackContent = assContent
        disposeTrack()
    }

    override fun frameSize(width: Int, height: Int) {
        if (released) return
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        renderer?.let(::applySize)
    }

    override fun render(timeMillis: Long): AssFrame? {
        if (released) return null
        val target: CPointer<ASS_Renderer> = activeRenderer() ?: return null
        val loaded: CPointer<ASS_Track> = activeTrack() ?: return null

        return memScoped {
            val changed = alloc<IntVar>()
            val head: CPointer<ASS_Image>? = ass_render_frame(target, loaded, timeMillis, changed.ptr)
            AssFrame(
                images = if (head == null) emptyList() else walk(head),
                changed = changed.value != 0,
            )
        }
    }

    private fun walk(head: CPointer<ASS_Image>): List<AssImage> {
        val images: MutableList<AssImage> = mutableListOf()
        var cursor: CPointer<ASS_Image>? = head

        while (cursor != null) {
            val image = cursor.pointed
            val size: Int = image.stride * image.h
            images += AssImage(
                x = image.dst_x,
                y = image.dst_y,
                width = image.w,
                height = image.h,
                stride = image.stride,
                colour = image.color.toInt(),
                pixels = image.bitmap?.readBytes(size) ?: ByteArray(0),
            )
            cursor = image.next
        }
        return images
    }

    private fun applySize(target: CPointer<ASS_Renderer>) {
        if (width == 0 || height == 0) return
        ass_set_storage_size(target, width, height)
        ass_set_frame_size(target, width, height)
    }

    private fun activeRenderer(): CPointer<ASS_Renderer>? {
        renderer?.let { return it }

        val created: CPointer<ASS_Renderer> = ass_renderer_init(library) ?: return null
        // Not optional. Without a font provider libass renders nothing, and
        // every other call still succeeds.
        ass_set_fonts(created, null, "sans-serif", FONT_PROVIDER_AUTODETECT, null, 1)

        // Before anything is drawn. libass defaults to 128MB of bitmap cache,
        // and the Apple target that matters most here is a television box with
        // a memory ceiling far below a phone's. The pair matches the other two
        // bindings: the glyph count has to track the megabytes, or the count
        // evicts first and every eviction re-rasterizes a glyph already held.
        ass_set_cache_limits(created, APPLE_GLYPH_MAX, APPLE_BITMAP_CACHE_MEGABYTES)
        applySize(created)
        renderer = created
        disposeTrack()
        return created
    }

    private fun activeTrack(): CPointer<ASS_Track>? {
        track?.let { return it }

        val content: ByteArray = trackContent?.encodeToByteArray() ?: return null
        if (content.isEmpty()) return null

        val loaded: CPointer<ASS_Track>? = memScoped {
            ass_read_memory(library, content.refTo(0), content.size.toULong(), null)
        }
        track = loaded
        return loaded
    }

    private fun disposeTrack() {
        track?.let { ass_free_track(it) }
        track = null
    }

    private fun disposeRenderer() {
        disposeTrack()
        renderer?.let { ass_renderer_done(it) }
        renderer = null
    }

    override fun release() {
        if (released) return
        released = true
        disposeRenderer()
        trackContent = null
        ass_library_done(library)
    }
}

internal const val FONT_PROVIDER_AUTODETECT: Int = 1

// A television box, not a desktop. tvOS gives an application far less than
// macOS would and the same build serves both it and the phone.
private const val APPLE_GLYPH_MAX: Int = 4_000
private const val APPLE_BITMAP_CACHE_MEGABYTES: Int = 16
