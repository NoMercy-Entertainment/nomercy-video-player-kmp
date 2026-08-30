// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.video.ass.render.RenderGeometry
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssImage
import com.sun.jna.Pointer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
// The cache budget is a PARAMETER, not a constant, because the two platforms
// that share this renderer do not share a heap. A 265MB television box and a
// desktop cannot afford the same glyph cache, and the numbers production found
// live per tier — see MemoryTier on Android.
internal class NativeAssRenderer(
    private val lib: LibAss,
    private val library: Pointer,
    private val glyphMax: Int,
    private val bitmapCacheMegabytes: Int,
) : AssRenderer {

    // A ReentrantLock, not `synchronized`, because [render] needs `tryLock` —
    // see there for why. Every other call still takes it unconditionally: they
    // run off the frame-render path (from the plugin's own suspend functions),
    // so blocking briefly there costs nothing a viewer can see.
    private val lock = ReentrantLock()

    private var renderer: Pointer? = null
    private var track: Pointer? = null
    private var trackContent: String? = null
    private var width: Int = 0
    private var height: Int = 0
    // Zero until a host or a track says otherwise, and zero means "the frame",
    // which is libass's own default rather than a guess of ours.
    private var storageWidth: Int = 0
    private var storageHeight: Int = 0
    private var hostStorage: Boolean = false
    private var released: Boolean = false

    override fun addFont(name: String, data: ByteArray): Unit = lock.withLock {
        if (released) return
        lib.ass_add_font(library, name, data, data.size)
        disposeRenderer()
    }

    // Drops every font the library holds. A queue advancing to a title with its
    // own attached fonts otherwise resolves against the previous one's, and that
    // failure is silent — the cue renders in a face that exists rather than the
    // one the disc carried.
    override fun clearFonts(): Unit = lock.withLock {
        if (released) return
        lib.ass_clear_fonts(library)
        disposeRenderer()
    }

    // Read off the content it was last handed, which it already keeps. An empty
    // script is how this renderer is told to draw nothing, and without a way to
    // ask, the layer could not tell "no cue due" from "no track at all" and kept
    // the last rasterised frame painted over the next film.
    override fun hasTrack(): Boolean = lock.withLock { !trackContent.isNullOrBlank() }

    override fun loadTrack(assContent: String): Unit = lock.withLock {
        if (released) return
        // Structural gate before ass_read_memory — see AssContentGuard.
        if (!looksLikeAssScript(assContent)) {
            trackContent = null
            disposeTrack()
            return
        }
        trackContent = assContent
        // Primed from the script, because one title ships tracks authored in
        // different spaces — No Game No Life's alternative track is 1280x720
        // where its full one is 1920x1080 — and a storage size left on the
        // previous track's value lays the new one out in the wrong coordinates.
        if (!hostStorage) {
            val primed: Pair<Int, Int>? = RenderGeometry.primeStorageFromScript(
                playRes(assContent, "PlayResX:"),
                playRes(assContent, "PlayResY:"),
            )
            storageWidth = primed?.first ?: 0
            storageHeight = primed?.second ?: 0
        }
        disposeTrack()
        renderer?.let { applySize(it) }
    }

    override fun storageSize(): AssSize? = lock.withLock {
        if (storageWidth > 0 && storageHeight > 0) AssSize(storageWidth, storageHeight) else null
    }

    override fun storageSize(width: Int, height: Int): Unit = lock.withLock {
        if (released) return
        // A host that knows the decoded size outranks the script, and keeps
        // outranking it: the next track must not quietly take the answer back.
        hostStorage = width > 0 && height > 0
        storageWidth = width.coerceAtLeast(0)
        storageHeight = height.coerceAtLeast(0)
        renderer?.let { applySize(it) }
    }

    // The header only. The full parse walks thirteen thousand events on a track
    // like this one, and this runs on the thread that is about to draw a frame.
    private fun playRes(assContent: String, key: String): Int =
        assContent.lineSequence()
            .take(HEADER_LINES)
            .firstOrNull { it.trimStart().startsWith(key, ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0

    override fun frameSize(width: Int, height: Int): Unit = lock.withLock {
        if (released) return
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        renderer?.let { applySize(it) }
    }

    // Called every drawn frame, on the same dispatcher a subtitle switch's
    // clearFonts()+addFont()×N+loadTrack() runs on — and that sequence can hold
    // the lock for real wall-clock time (parsing a script, decoding fonts over
    // JNI). Blocking here waited for it, which starved every other frame queued
    // on the same pool behind this call and read on screen as playback
    // stuttering on every subtitle switch, ASS or VTT alike (a VTT selection
    // still calls clearTrack(), same lock). `tryLock` instead: a frame that
    // lands mid-switch is answered `changed = false`, which the caller already
    // treats as "keep what's on screen" — exactly right for one dropped
    // subtitle redraw, and it costs the video nothing.
    override fun render(timeMillis: Long): AssFrame? {
        if (!lock.tryLock()) return AssFrame(images = emptyList(), changed = false)
        try {
            if (released) return null
            val target: Pointer = activeRenderer() ?: return null
            val loaded: Pointer = activeTrack() ?: return null

            val changed = IntArray(1)
            val head: Pointer? = lib.ass_render_frame(target, loaded, timeMillis, changed)
            return AssFrame(
                images = if (head == null) emptyList() else walk(head),
                changed = changed[0] != 0,
            )
        } finally {
            lock.unlock()
        }
    }

    // libass returns a linked list it owns, valid until the next render. Copying
    // out here is what stops the caller holding memory libass is about to reuse.
    //
    // Into buffers this renderer keeps, not fresh ones. An ending sequence is
    // two hundred glyph runs a frame, and allocating a coverage array for each
    // was a million arrays over a two-minute window — enough garbage that the
    // collector's pauses, not the rendering, were what dropped frames. Run n
    // is close to the same size frame after frame, so a buffer per position
    // stops growing almost immediately.
    //
    // The consequence is the lifetime in AssFrame's documentation: the arrays
    // belong to the renderer and the next render overwrites them. That is the
    // same lifetime libass itself gives, one step further out.
    private val coverage: MutableList<ByteArray> = mutableListOf()

    private fun walk(head: Pointer): List<AssImage> {
        val images: MutableList<AssImage> = mutableListOf()
        var cursor: Pointer? = head
        var run = 0

        while (cursor != null) {
            val image = AssImageStruct(cursor)
            val size: Int = image.stride * image.h
            val pixels: ByteArray = borrow(run, size)

            // libass owns `stride * (h - 1) + w` bytes, not `stride * h`: every
            // row but the last is padded to the stride, and the last one stops
            // at the glyph's width. Reading the declared rectangle walked past
            // the end of that allocation and off the page — SIGSEGV in
            // __memcpy_aarch64_simd, from inside a coroutine, on a real phone
            // mid-episode.
            //
            // The array stays the declared size so every consumer can still
            // index `y * stride + x`; only the copy stops where the pixels do.
            // The tail of the last row is padding nothing reads.
            val readable: Int = readableBytes(image.stride, image.h, image.w)
            if (readable > 0) image.bitmap?.read(0, pixels, 0, readable)

            images += AssImage(
                x = image.dstX,
                y = image.dstY,
                width = image.w,
                height = image.h,
                stride = image.stride,
                colour = image.color,
                pixels = pixels,
            )
            cursor = image.next
            run++
        }
        return images
    }

    // Exactly the declared size, because AssImage.stride and height describe the
    // array and a longer one would be read past its content by anything that
    // trusted its length instead.
    private fun borrow(run: Int, size: Int): ByteArray {
        while (coverage.size <= run) coverage += ByteArray(0)
        val held: ByteArray = coverage[run]
        if (held.size == size) return held

        val fresh = ByteArray(size)
        coverage[run] = fresh
        return fresh
    }

    private fun applySize(target: Pointer) {
        if (width == 0 || height == 0) return
        val storedWidth: Int = if (storageWidth > 0) storageWidth else width
        val storedHeight: Int = if (storageHeight > 0) storageHeight else height
        lib.ass_set_storage_size(target, storedWidth, storedHeight)
        lib.ass_set_frame_size(target, width, height)
    }

    private fun activeRenderer(): Pointer? {
        renderer?.let { return it }

        val created: Pointer = lib.ass_renderer_init(library) ?: return null
        // Not optional. Without a font provider libass renders nothing, and
        // every other call still succeeds.
        // The carried face as the fallback, and the system provider on top of it.
        //
        // A machine whose provider answers nothing renders every cue as no
        // glyphs at all, silently. Passing a path here is what makes the
        // difference: with it, all three render gates draw; without it they
        // fail with "failed to find any fallback with glyph 0x0" on a box
        // where fontconfig resolves the same family perfectly at the shell.
        lib.ass_set_fonts(created, FallbackFont.path, "sans-serif", FONT_PROVIDER_AUTODETECT, null, 1)

        // Before anything is drawn. libass defaults to 128MB of bitmap cache,
        // which is a number chosen for a desktop with headroom to spare — and
        // this renderer ends up inside consumer applications with budgets of
        // their own. The pair matches Android's largest tier: the glyph count
        // has to track the megabytes or the count evicts first and every
        // eviction sends FreeType back over a glyph it already had.
        lib.ass_set_cache_limits(created, glyphMax, bitmapCacheMegabytes)
        applySize(created)
        renderer = created
        disposeTrack()
        return created
    }

    private fun activeTrack(): Pointer? {
        track?.let { return it }

        val content: ByteArray = trackContent?.encodeToByteArray() ?: return null
        // Backstop for whatever looksLikeAssScript doesn't catch — JNA exceptions are catchable here.
        val loaded: Pointer = runCatching {
            lib.ass_read_memory(library, content, content.size, null)
        }.getOrNull() ?: return null
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

    override fun release(): Unit = lock.withLock {
        if (released) return
        released = true
        disposeRenderer()
        trackContent = null
        lib.ass_library_done(library)
    }

    private companion object {
        // Every ASS header ends well inside this; [Events] on the densest track
        // here is line 40.
        const val HEADER_LINES = 200
    }
}

/**
 * How many bytes of an `ASS_Image` bitmap libass actually owns.
 *
 * Every row but the last is padded out to the stride; the last one stops at the
 * glyph's width. `stride * h` is the rectangle the struct describes and up to
 * `stride - w` bytes more than the allocation, which on a bitmap that ends on a
 * page boundary is a segfault rather than a stale byte.
 */
internal fun readableBytes(stride: Int, height: Int, width: Int): Int =
    if (stride <= 0 || height <= 0 || width <= 0) 0 else stride * (height - 1) + width
