// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import android.graphics.Bitmap
import io.github.peerless2012.ass.Ass
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.AssTexType
import io.github.peerless2012.ass.AssTrack
import tv.nomercy.player.video.ass.render.MemoryTier
import tv.nomercy.player.video.ass.render.renderScaleFor
import java.nio.ByteBuffer

// libass on Android, over the published JNI binding the app already ships.
//
// The same binding rather than a second one on purpose: two libass integrations
// in one ecosystem is two sets of font-resolution behaviour, and a subtitle that
// renders differently in the app and in the library is a bug report nobody can
// reproduce.
//
// Every native call is serialized. libass contexts are not thread-safe, and a
// render racing an addFont is a native crash with a Kotlin stack that names
// neither.
internal class AndroidAssRenderer(
    private val tier: MemoryTier = MemoryTier.MEDIUM,
) : AssRenderer {

    private val lock = Any()

    private val ass: Ass = Ass()
    private var renderer: AssRender? = null
    private var track: AssTrack? = null
    private var released: Boolean = false
    private var width: Int = 0
    private var height: Int = 0
    private var trackContent: String? = null

    // Fonts go in before the renderer exists, and adding one after it does
    // throws the renderer away.
    //
    // libass builds its font provider when the renderer is initialised, and a
    // font added to the library afterwards is not in it. The failure is a cue
    // drawn in a fallback face with nothing reported — the subtitle appears, so
    // nothing looks wrong until someone who knows the show says the typeface is
    // not the one on the disc. Rebuilding costs a font-cache pass; being subtly
    // wrong costs a bug nobody can describe.
    override fun addFont(name: String, data: ByteArray): Unit = synchronized(lock) {
        if (released) return
        ass.addFont(name, data)
        renderer = null
    }

    // Dropping every font the library holds, for the same reason addFont
    // rebuilds the renderer: libass resolves against what it was given, and an
    // episode carrying a different set would otherwise draw in the previous
    // one's typeface without reporting anything.
    fun clearFonts(): Unit = synchronized(lock) {
        if (released) return
        ass.clearFont()
        renderer = null
    }

    override fun loadTrack(assContent: String): Unit = synchronized(lock) {
        if (released) return
        trackContent = assContent
        track = null
    }

    override fun frameSize(width: Int, height: Int): Unit = synchronized(lock) {
        if (released) return
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        renderer?.let { applySize(it) }
    }

    // Storage size is the resolution the subtitle was authored against and frame
    // size is what it is drawn onto. Setting only one scales the text wrong on
    // any screen that is not the author's.
    //
    // Both are clamped to what the device can afford. A 256MB television asked
    // to rasterize a 1080p overlay spends more on the subtitle than on the video
    // frame beneath it, and libass draws the same glyphs at 720p — the surface
    // scales the result back up, which for anti-aliased text nobody can pick out
    // at viewing distance.
    private fun applySize(target: AssRender) {
        if (width == 0 || height == 0) return

        val scale: Double = renderScaleFor(width, height, tier)
        val renderWidth: Int = (width * scale).toInt().coerceAtLeast(1)
        val renderHeight: Int = (height * scale).toInt().coerceAtLeast(1)

        target.setStorageSize(renderWidth, renderHeight)
        target.setFrameSize(renderWidth, renderHeight)
    }

    private fun activeRenderer(): AssRender {
        val existing: AssRender? = renderer
        if (existing != null) return existing

        val created: AssRender = ass.createRender()

        // Before anything is drawn, because the default is 128MB. On a 248MB
        // heap that left so little room that collection ran for eight and nine
        // seconds at a time and the player was reported stuck.
        created.setCacheLimit(tier.glyphCacheMax, tier.libassCacheMegabytes)
        applySize(created)
        renderer = created
        track = null
        return created
    }

    private fun activeTrack(target: AssRender): AssTrack? {
        val existing: AssTrack? = track
        if (existing != null) return existing

        val content: String = trackContent ?: return null
        val loaded: AssTrack = ass.createTrack()
        loaded.readBuffer(content.toByteArray(Charsets.UTF_8))
        target.setTrack(loaded)
        track = loaded
        return loaded
    }

    override fun render(timeMillis: Long): AssFrame? = synchronized(lock) {
        if (released) return null
        val target: AssRender = activeRenderer()
        if (activeTrack(target) == null) return null

        val frame = target.renderFrame(timeMillis, AssTexType.BITMAP_ALPHA) ?: return null
        return AssFrame(
            images = frame.images.orEmpty().mapNotNull { texture ->
                texture.bitmap?.let { bitmap ->
                    AssImage(
                        x = texture.x,
                        y = texture.y,
                        width = texture.w,
                        height = texture.h,
                        stride = texture.w,
                        colour = texture.color,
                        pixels = coverageOf(bitmap),
                    )
                }
            },
            changed = frame.changed != 0,
        )
    }

    // The binding hands back an ALPHA_8 bitmap; the contract is one coverage
    // byte per pixel. Copying it out here means the caller never has to know a
    // Bitmap was involved, which is what keeps the common contract free of
    // Android.
    private fun coverageOf(bitmap: Bitmap): ByteArray {
        val bytes = ByteArray(bitmap.width * bitmap.height)
        bitmap.copyPixelsToBuffer(ByteBuffer.wrap(bytes))
        return bytes
    }

    // Dropping references is the whole of it, because the binding exposes no
    // explicit teardown — it frees the native contexts in finalize(). That is
    // the binding's design and not a choice made here; what this can do is stop
    // handing out work after release, which is what `released` is for. A render
    // call into a freed context is a native crash, and it would be reported
    // against the app rather than against the library that let it happen.
    override fun release(): Unit = synchronized(lock) {
        released = true
        track = null
        renderer = null
        trackContent = null
    }
}
