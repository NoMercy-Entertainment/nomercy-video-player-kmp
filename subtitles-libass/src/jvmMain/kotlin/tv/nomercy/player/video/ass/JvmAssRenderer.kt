// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import com.sun.jna.Pointer

// libass on the JVM, over JNA.
//
// The renderer is built lazily and thrown away when a font arrives after it,
// exactly as on Android and for the same reason: libass builds its font provider
// when the renderer is initialised, so a font added afterwards is one it has
// already decided not to use. The failure is a cue in a fallback face with
// nothing reported.
//
// Every native call is serialized. libass contexts are not thread-safe, and a
// render racing an add is a native crash whose Kotlin stack names neither.
internal class JvmAssRenderer(private val lib: LibAss, private val library: Pointer) : AssRenderer {

    private val lock = Any()

    private var renderer: Pointer? = null
    private var track: Pointer? = null
    private var trackContent: String? = null
    private var width: Int = 0
    private var height: Int = 0
    private var released: Boolean = false

    override fun addFont(name: String, data: ByteArray): Unit = synchronized(lock) {
        if (released) return
        lib.ass_add_font(library, name, data, data.size)
        disposeRenderer()
    }

    override fun loadTrack(assContent: String): Unit = synchronized(lock) {
        if (released) return
        trackContent = assContent
        disposeTrack()
    }

    override fun frameSize(width: Int, height: Int): Unit = synchronized(lock) {
        if (released) return
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        renderer?.let { applySize(it) }
    }

    override fun render(timeMillis: Long): AssFrame? = synchronized(lock) {
        if (released) return null
        val target: Pointer = activeRenderer() ?: return null
        val loaded: Pointer = activeTrack() ?: return null

        val changed = IntArray(1)
        val head: Pointer? = lib.ass_render_frame(target, loaded, timeMillis, changed)
        return AssFrame(
            images = if (head == null) emptyList() else walk(head),
            changed = changed[0] != 0,
        )
    }

    // libass returns a linked list it owns, valid until the next render. Copying
    // out here is what stops the caller holding memory libass is about to reuse.
    private fun walk(head: Pointer): List<AssImage> {
        val images: MutableList<AssImage> = mutableListOf()
        var cursor: Pointer? = head

        while (cursor != null) {
            val image = AssImageStruct(cursor)
            val coverage: ByteArray = image.bitmap
                ?.getByteArray(0, image.stride * image.h)
                ?: ByteArray(0)

            images += AssImage(
                x = image.dstX,
                y = image.dstY,
                width = image.w,
                height = image.h,
                stride = image.stride,
                colour = image.color,
                pixels = coverage,
            )
            cursor = image.next
        }
        return images
    }

    private fun applySize(target: Pointer) {
        if (width == 0 || height == 0) return
        lib.ass_set_storage_size(target, width, height)
        lib.ass_set_frame_size(target, width, height)
    }

    private fun activeRenderer(): Pointer? {
        renderer?.let { return it }

        val created: Pointer = lib.ass_renderer_init(library) ?: return null
        // Not optional. Without a font provider libass renders nothing, and
        // every other call still succeeds.
        lib.ass_set_fonts(created, null, "sans-serif", FONT_PROVIDER_AUTODETECT, null, 1)
        applySize(created)
        renderer = created
        disposeTrack()
        return created
    }

    private fun activeTrack(): Pointer? {
        track?.let { return it }

        val content: ByteArray = trackContent?.encodeToByteArray() ?: return null
        val loaded: Pointer = lib.ass_read_memory(library, content, content.size, null) ?: return null
        track = loaded
        return loaded
    }

    private fun disposeTrack() {
        track?.let { lib.ass_free_track(it) }
        track = null
    }

    private fun disposeRenderer() {
        // A track belongs to the renderer that drew it, so it goes first;
        // keeping one across a rebuild is a pointer into a context that no
        // longer exists.
        disposeTrack()
        renderer?.let { lib.ass_renderer_done(it) }
        renderer = null
    }

    override fun release(): Unit = synchronized(lock) {
        if (released) return
        released = true
        disposeRenderer()
        trackContent = null
        lib.ass_library_done(library)
    }
}
