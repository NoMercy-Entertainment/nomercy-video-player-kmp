// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.bench

import com.sun.jna.Native
import com.sun.jna.Pointer
import tv.nomercy.player.video.ass.AssImageStruct
import tv.nomercy.player.video.ass.FONT_PROVIDER_AUTODETECT
import tv.nomercy.player.video.ass.LibAss
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

// Where a cue lands, at a given surface size, with storage set two different
// ways.
//
// The layer draws subtitles too large and clips the long lines at the right
// edge. That is either libass being told the wrong geometry or the Compose
// side drawing a correct bitmap at the wrong size, and the two are only
// separable off-screen: this renders the same cue with no window involved and
// reports the extents in pixels.
object AssGeometryProbe {

    @JvmStatic
    @Suppress("LongMethod")
    fun main(arguments: Array<String>) {
        val libraryPath: String = given("libass") ?: error("-Pprobe.libass is required")
        val track = File(given("track") ?: error("-Pprobe.track is required"))
        val fontDir = File(given("fonts") ?: track.parentFile.resolve("fonts").path)
        val out = File(given("out") ?: track.parentFile.path)
        val timeMs: Long = given("time")?.toLong() ?: 0L
        val width: Int = given("width")?.toInt() ?: 1920
        val height: Int = given("height")?.toInt() ?: 1080

        val lib: LibAss = Native.load(libraryPath, LibAss::class.java)
        val body: ByteArray = track.readBytes()
        val header: String = String(body, 0, minOf(body.size, 4096), Charsets.UTF_8)
        val playResX: Int = valueOf(header, "PlayResX:")
        val playResY: Int = valueOf(header, "PlayResY:")
        println("track ${track.name} PlayRes ${playResX}x$playResY, surface ${width}x$height, t=${timeMs}ms")

        for (storage in listOf(width to height, playResX to playResY)) {
            val library: Pointer = lib.ass_library_init() ?: error("ass_library_init")
            for (font in fontDir.listFiles()?.sorted().orEmpty()) {
                lib.ass_add_font(library, font.name, font.readBytes(), font.length().toInt())
            }
            val renderer: Pointer = lib.ass_renderer_init(library) ?: error("ass_renderer_init")
            lib.ass_set_fonts(renderer, null, "sans-serif", FONT_PROVIDER_AUTODETECT, null, 1)
            lib.ass_set_storage_size(renderer, storage.first, storage.second)
            lib.ass_set_frame_size(renderer, width, height)

            val parsed: Pointer = lib.ass_read_memory(library, body, body.size, null) ?: error("ass_read_memory")
            val head: Pointer? = lib.ass_render_frame(renderer, parsed, timeMs, null)

            var left = Int.MAX_VALUE
            var top = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            var bottom = Int.MIN_VALUE
            var runs = 0

            val picture = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            var cursor: Pointer? = head
            while (cursor != null) {
                val image = AssImageStruct(cursor)
                runs++
                left = minOf(left, image.dstX)
                top = minOf(top, image.dstY)
                right = maxOf(right, image.dstX + image.w)
                bottom = maxOf(bottom, image.dstY + image.h)
                paint(picture, image, width, height)
                cursor = image.next
            }

            val label = "storage-${storage.first}x${storage.second}"
            println("  $label: $runs runs, extent ${left},$top .. ${right},$bottom" +
                (if (right > width) "  OVERFLOWS RIGHT by ${right - width}px" else ""))
            if (runs > 0) ImageIO.write(picture, "png", out.resolve("probe-$label-${timeMs}.png"))
        }
    }

    private fun paint(picture: BufferedImage, image: AssImageStruct, width: Int, height: Int) {
        val size: Int = image.stride * image.h
        if (size <= 0) return
        val coverage = ByteArray(size)
        image.bitmap?.read(0, coverage, 0, size)

        val red: Int = (image.color ushr 24) and 0xFF
        val green: Int = (image.color ushr 16) and 0xFF
        val blue: Int = (image.color ushr 8) and 0xFF
        val opacity: Int = 0xFF - (image.color and 0xFF)

        for (y in 0 until image.h) {
            val row: Int = image.dstY + y
            if (row < 0 || row >= height) continue
            for (x in 0 until image.w) {
                val column: Int = image.dstX + x
                if (column < 0 || column >= width) continue
                val alpha: Int = (coverage[y * image.stride + x].toInt() and 0xFF) * opacity / 0xFF
                if (alpha == 0) continue
                picture.setRGB(column, row, (alpha shl 24) or (red shl 16) or (green shl 8) or blue)
            }
        }
    }

    // A gradle property that was never passed arrives as an empty string, not
    // as an absent one, and every default below would parse that as a number.
    private fun given(name: String): String? = System.getProperty("nomercy.probe.$name")?.ifBlank { null }

    private fun valueOf(header: String, key: String): Int =
        header.lineSequence()
            .firstOrNull { it.trim().startsWith(key, ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
}
