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
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssImage
import tv.nomercy.player.video.subtitles.AssFrameCompositor
import tv.nomercy.player.video.subtitles.AssChangedRows
import tv.nomercy.player.video.subtitles.AssSurfaceFrame
import kotlinx.coroutines.runBlocking
import java.io.File
import tv.nomercy.player.video.ass.readableBytes

// What a moving ASS subtitle actually costs on the desktop, measured on the
// real tracks that are slow rather than on a synthetic one.
//
// Isolated on purpose: no video decode, no Compose, no window. A player that
// drops frames during an anime opening has three candidate causes — libass
// itself, the copy out of libass's linked list, and our own compositor — and
// they are only separable when nothing else is competing for the CPU. Timing
// them inside a running player measures the scheduler.
//
// Run it with:
//   ./gradlew :subtitles-libass:assBenchmark
//
// -Dnomercy.bench.libass=<path to libass-9.dll/.so/.dylib>
// -Dnomercy.bench.assets=<directory holding the staged tracks>
object AssRenderBenchmark {

    private const val FRAME_WIDTH = 1920
    private const val FRAME_HEIGHT = 1080

    // The desktop's production budget, from AssRenderers.jvm.kt. A benchmark
    // that picked its own numbers would measure a renderer nobody ships.
    private const val GLYPH_MAX = 6_000
    private const val BITMAP_CACHE_MEGABYTES = 32

    // Windows chosen from the tracks themselves, by counting how many dialogue
    // lines carrying \k, \move, \t or \fad are live in each second. These are
    // the peaks, not a sample of the middle.
    private val scenarios: List<Scenario> = listOf(
        Scenario("no-rin OP", "no-rin", 60_000, 120_000),
        Scenario("no-rin heavy typeset", "no-rin", 930_000, 960_000),
        Scenario("no-rin ED", "no-rin", 1_405_000, 1_420_000),
        Scenario("ngnl-e07 OP", "ngnl-e07", 80_000, 180_000),
        Scenario("ngnl-e07 ED (SPLAT)", "ngnl-e07", 1_350_000, 1_425_000),
    )

    @JvmStatic
    fun main(arguments: Array<String>) {
        val libraryPath: String = System.getProperty("nomercy.bench.libass")
            ?: error("-Dnomercy.bench.libass=<path to the libass shared library> is required")
        val assets = File(System.getProperty("nomercy.bench.assets") ?: error("-Dnomercy.bench.assets is required"))

        val lib: LibAss = Native.load(libraryPath, LibAss::class.java)
        println("libass loaded from $libraryPath")
        println("surface ${FRAME_WIDTH}x$FRAME_HEIGHT, cache ${GLYPH_MAX} glyphs / ${BITMAP_CACHE_MEGABYTES}MB, "
            + "compositing in $bands band(s)")
        println("jvm ${System.getProperty("java.version")} on ${System.getProperty("os.name")}, " +
            "${Runtime.getRuntime().availableProcessors()} cores")

        // A filter, because a contended machine makes a measurement worthless
        // and the only way to know is to re-run the same window on a quiet one.
        // Sitting through the four scenarios that were already clean to get
        // back to the one that was not is how a run ends up sharing the box
        // with whatever was waiting for it.
        val only: String = System.getProperty("nomercy.bench.only").orEmpty()
        val selected: List<Scenario> = scenarios.filter { only.isEmpty() || it.label.contains(only) }
        check(selected.isNotEmpty()) { "no scenario matches '$only'" }

        for (scenario in selected) {
            runScenario(lib, assets.resolve(scenario.asset), scenario)
        }
    }

    private fun runScenario(lib: LibAss, assetDirectory: File, scenario: Scenario) {
        val library: Pointer = lib.ass_library_init() ?: error("ass_library_init returned null")
        val fonts: List<File> = assetDirectory.resolve("fonts").listFiles()?.sorted().orEmpty()
        for (font in fonts) {
            lib.ass_add_font(library, font.name, font.readBytes(), font.length().toInt())
        }

        val renderer: Pointer = lib.ass_renderer_init(library) ?: error("ass_renderer_init returned null")
        lib.ass_set_fonts(renderer, null, "sans-serif", FONT_PROVIDER_AUTODETECT, null, 1)
        lib.ass_set_cache_limits(renderer, GLYPH_MAX, BITMAP_CACHE_MEGABYTES)
        lib.ass_set_storage_size(renderer, FRAME_WIDTH, FRAME_HEIGHT)
        lib.ass_set_frame_size(renderer, FRAME_WIDTH, FRAME_HEIGHT)

        val content: ByteArray = assetDirectory.resolve("track.ass").readBytes()
        val track: Pointer = lib.ass_read_memory(library, content, content.size, null)
            ?: error("ass_read_memory returned null")

        // One compositor for the whole scenario, which is what a player holds:
        // benchmarking a fresh one per frame would measure the allocation this
        // exists to avoid.
        val compositor = AssFrameCompositor()
        // The desktop surface's byte buffers, one per compositor buffer, which
        // is what AssPictureSurface keeps.
        val surfaces: Array<ByteArray> = Array(2) { ByteArray(FRAME_WIDTH * FRAME_HEIGHT * BYTES_PER_PIXEL) }

        // Warm-up, discarded. The first pass pays for JIT, for FreeType opening
        // every font and for a cold glyph cache, and reporting that as the cost
        // of a frame would be measuring the first second of playback forever.
        step(lib, renderer, track, scenario, compositor, surfaces, STEP_60FPS_MS) { _, _, _, _, _ -> }

        for (fps in intArrayOf(60, 120)) {
            val stepMs: Long = 1000L / fps

            // Primitive arrays, sized up front, because the ruler was adding to
            // what it measured.
            //
            // These were MutableList<Long>, which boxes every sample: four lists
            // times ten thousand frames is forty thousand short-lived objects per
            // block, allocated between the timed sections and collected during
            // them. The distribution grew 20-110ms outliers over a 3ms median and
            // they were read as the renderer stalling. Nothing is allocated in
            // the loop now.
            val capacity: Int = ((scenario.endMs - scenario.startMs) / stepMs).toInt() + 2
            val native = LongArray(capacity)
            val copy = LongArray(capacity)
            val composite = LongArray(capacity)
            val upload = LongArray(capacity)
            var frames = 0
            var changedFrames = 0
            var runs = 0L
            var peakRuns = 0

            step(lib, renderer, track, scenario, compositor, surfaces, stepMs) { nativeNs, copyNs, compositeNs, uploadNs, images ->
                native[frames] = nativeNs
                copy[frames] = copyNs
                composite[frames] = compositeNs
                upload[frames] = uploadNs
                frames++
                if (compositeNs > 0) changedFrames++
                runs += images
                if (images > peakRuns) peakRuns = images
            }

            report(scenario, fps, stepMs, frames, changedFrames, runs, peakRuns, native, copy, composite, upload)
        }

        lib.ass_free_track(track)
        lib.ass_renderer_done(renderer)
        lib.ass_library_done(library)
    }

    // One pass over the window. The callback gets the three stages separately,
    // because "the subtitle layer is slow" is not actionable and "the copy out
    // of libass costs four times what libass does" is.
    private inline fun step(
        lib: LibAss,
        renderer: Pointer,
        track: Pointer,
        scenario: Scenario,
        compositor: AssFrameCompositor,
        surfaces: Array<ByteArray>,
        stepMs: Long,
        onFrame: (nativeNs: Long, copyNs: Long, compositeNs: Long, uploadNs: Long, runs: Int) -> Unit,
    ) {
        converted = 0L
        surfaceFrames = 0
        var timeMs: Long = scenario.startMs
        while (timeMs <= scenario.endMs) {
            val changed = IntArray(1)

            val nativeStart: Long = System.nanoTime()
            val head: Pointer? = lib.ass_render_frame(renderer, track, timeMs, changed)
            val nativeNs: Long = System.nanoTime() - nativeStart

            val copyStart: Long = System.nanoTime()
            val images: List<AssImage> = if (head == null) emptyList() else walk(head)
            val copyNs: Long = System.nanoTime() - copyStart

            // Exactly what AssSubtitleLayer does: an unchanged frame is not
            // composited at all, so counting it would flatter the average.
            var compositeNs = 0L
            var uploadNs = 0L
            if (changed[0] != 0) {
                val compositeStart: Long = System.nanoTime()
                val composited: AssSurfaceFrame = runBlocking {
                    compositor.renderParallel(images, FRAME_WIDTH, FRAME_HEIGHT, bands)
                }
                compositeNs = System.nanoTime() - compositeStart

                val uploadStart: Long = System.nanoTime()
                toBgraBytes(composited.pixels, surfaces[composited.slot], composited.changed, FRAME_WIDTH)
                uploadNs = System.nanoTime() - uploadStart

                converted += changedPixels(composited.changed)
                surfaceFrames++
            }

            onFrame(nativeNs, copyNs, compositeNs, uploadNs, images.size)
            timeMs += stepMs
        }
    }

    // The ARGB-to-BGRA conversion the desktop surface does, over the band the
    // compositor says changed.
    //
    // Reproduced rather than called because AssPictureSurface lives in
    // ui-compose, and pulling Compose and Skia into a subtitle benchmark would
    // put a windowing toolkit between the measurement and the thing measured.
    // Only Skia's own installPixels is left out; everything the JVM does is
    // here, and it is the part that scales with the surface.
    private fun toBgraBytes(pixels: IntArray, bytes: ByteArray, changed: AssChangedRows, stride: Int) {
        if (changed.isEmpty) return

        for (y in changed.top..changed.bottom) {
            val left: Int = changed.leftAt(y)
            val right: Int = changed.rightAt(y)
            if (left > right) continue

            var source: Int = y * stride + left
            var target: Int = source * BYTES_PER_PIXEL

            for (x in left..right) {
                val pixel: Int = pixels[source++]
                bytes[target++] = pixel.toByte()
                bytes[target++] = (pixel ushr 8).toByte()
                bytes[target++] = (pixel ushr 16).toByte()
                bytes[target++] = (pixel ushr 24).toByte()
            }
        }
    }

    // How much of the surface the changed rows actually cover, which is the
    // number that says whether spanning by row was worth doing.
    private fun changedPixels(changed: AssChangedRows): Long {
        if (changed.isEmpty) return 0L
        var total = 0L
        for (y in changed.top..changed.bottom) {
            val span: Int = changed.rightAt(y) - changed.leftAt(y) + 1
            if (span > 0) total += span
        }
        return total
    }

    // The copy NativeAssRenderer performs, reproduced here so its cost is
    // visible on its own rather than folded into the native call.
    private val coverage: MutableList<ByteArray> = mutableListOf()

    private fun walk(head: Pointer): List<AssImage> {
        val images: MutableList<AssImage> = mutableListOf()
        var cursor: Pointer? = head
        var run = 0

        while (cursor != null) {
            val image = AssImageStruct(cursor)
            val size: Int = image.stride * image.h
            val pixels: ByteArray = borrow(run, size)
            // The allocation, not the rectangle — see readableBytes.
            image.bitmap?.read(0, pixels, 0, readableBytes(image.stride, image.h, image.w))

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

    private fun borrow(run: Int, size: Int): ByteArray {
        while (coverage.size <= run) coverage += ByteArray(0)
        val held: ByteArray = coverage[run]
        if (held.size == size) return held

        val fresh = ByteArray(size)
        coverage[run] = fresh
        return fresh
    }

    @Suppress("LongParameterList")
    private fun report(
        scenario: Scenario,
        fps: Int,
        stepMs: Long,
        frames: Int,
        changedFrames: Int,
        runs: Long,
        peakRuns: Int,
        native: LongArray,
        copy: LongArray,
        composite: LongArray,
        upload: LongArray,
    ) {
        // Only the frames that ran. The arrays are sized for the window and the
        // last one is short whenever the step does not divide it exactly.
        val totals = LongArray(frames) { native[it] + copy[it] + composite[it] + upload[it] }
        val budgetNs: Long = stepMs * 1_000_000L
        val missed: Int = totals.count { it > budgetNs }

        println()
        println("--- ${scenario.label} @ ${fps}fps (${scenario.startMs / 1000}s..${scenario.endMs / 1000}s) ---")
        println("frames=$frames changed=$changedFrames glyph-runs total=$runs peak=$peakRuns")
        if (surfaceFrames > 0) {
            val average: Long = converted / surfaceFrames
            val surface: Long = FRAME_WIDTH.toLong() * FRAME_HEIGHT
            println("  changed area avg ${average / 1000}k px of ${surface / 1000}k " +
                "(${"%.1f".format(average * 100.0 / surface)}% of the frame)")
        }
        line("libass  ", native, frames)
        line("copy-out", copy, frames)
        line("composite", composite, frames)
        line("argb->bgra", upload, frames)
        line("TOTAL   ", totals, frames)
        val worst: Double = totals.max() / 1_000_000.0
        println(
            "  budget ${stepMs}ms -> MISSED $missed/$frames frames " +
                "(${"%.1f".format(missed * 100.0 / frames)}%), worst ${"%.2f".format(worst)}ms",
        )
    }

    private fun line(label: String, samples: LongArray, frames: Int) {
        val sorted: LongArray = samples.copyOf(frames).also { it.sort() }
        fun at(fraction: Double): Double =
            sorted[(fraction * (sorted.size - 1)).toInt()] / 1_000_000.0
        println(
            "  $label p50=${"%7.3f".format(at(0.50))}ms  p95=${"%7.3f".format(at(0.95))}ms  " +
                "p99=${"%7.3f".format(at(0.99))}ms  max=${"%7.3f".format(at(1.0))}ms",
        )
    }

    // How many strips the compositor blends at once. Settable because the
    // number that helps a 32-core desktop is not the number that helps a
    // television, and the only honest way to pick one is to measure both.
    private val bands: Int = System.getProperty("nomercy.bench.bands")?.toIntOrNull() ?: 8

    private const val STEP_60FPS_MS = 16L
    private const val BYTES_PER_PIXEL = 4

    // How many pixels the surface actually had to convert, against how many the
    // frame holds. The number that says whether spanning by row beat a bounding
    // box, rather than an argument that it must.
    private var converted: Long = 0L
    private var surfaceFrames: Int = 0

    private data class Scenario(
        val label: String,
        val asset: String,
        val startMs: Long,
        val endMs: Long,
    )
}
