// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// libass packs a colour as 0xRRGGBBAA and its last byte is INVERSE alpha: zero
// is opaque and 255 is invisible. Reading it as ordinary alpha draws every
// subtitle at exactly the transparency it should not have, which for the common
// fully-opaque case means drawing nothing at all.
public fun assArgbOf(libassColour: Int): Int {
    val red: Int = (libassColour ushr RED_BYTE) and BYTE_MASK
    val green: Int = (libassColour ushr GREEN_BYTE) and BYTE_MASK
    val blue: Int = (libassColour ushr BLUE_BYTE) and BYTE_MASK
    val alpha: Int = BYTE_MASK - (libassColour and BYTE_MASK)

    return (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

/**
 * Lays libass's glyph runs over each other into one frame of ARGB pixels.
 *
 * libass hands back a run of glyphs at a time — an 8-bit coverage mask, a
 * colour, and a position in frame coordinates — and something has to lay those
 * over each other in order. Doing it per-surface is how two surfaces come to
 * disagree about which glyph is on top; an outline drawn over the glyph it
 * outlines is the visible version of that.
 *
 * The pixels are PREMULTIPLIED. Straight alpha would need a divide per channel
 * per pixel to get back out of the blend, and both toolkits this feeds want
 * premultiplied anyway — Skia converts to it before drawing and an Android
 * ARGB_8888 bitmap already is it. The conversion was being done twice, once
 * here and once by the toolkit, to arrive back where the blend started.
 *
 * Allocating per call. [AssFrameCompositor] is the one to hold when frames
 * arrive in a sequence, which is what a player does; this exists for a caller
 * with a single frame and for tests, where a fresh buffer is the clearer
 * contract.
 */
public fun compositeAssFrame(
    images: List<AssImage>,
    width: Int,
    height: Int,
): IntArray = AssFrameCompositor().composite(images, width, height)

/**
 * The same compositing, across a sequence of frames.
 *
 * A subtitle frame is a 1920x1080 buffer whether one glyph moved or the whole
 * screen changed, and allocating and zeroing one per frame is eight megabytes
 * of memset thirty times a second before a single glyph is drawn. Measured on
 * No Game No Life's ending, that allocation and the straight-alpha blend
 * together cost 14ms a frame against libass's own 1.6ms — the subtitle layer
 * was nine times more expensive than the subtitle renderer.
 *
 * So the buffers are kept and only what was written last time is cleared. Two
 * of them in rotation, because the frame handed out last is on screen and being
 * drawn from: writing into it would repaint a frame the toolkit already owns,
 * which is the tearing the per-call allocation was avoiding.
 *
 * NOT thread-safe, deliberately. One compositor belongs to one subtitle layer
 * and its frames are produced in order on one coroutine; a lock here would be
 * paid on every frame to guard against a caller that does not exist.
 */
public class AssFrameCompositor {

    private var buffers: Array<IntArray> = emptyArray()
    private var current: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private var generation: Int = 0

    // What was written into each buffer, so the next use of it clears that and
    // nothing else. Per buffer rather than per frame: a buffer comes back round
    // two frames later and it is still holding what IT was given, not what the
    // frame in between drew.
    private var drawn: Array<AssChangedRows> = emptyArray()

    // The union of what this buffer held and what was just drawn into it, which
    // is what a surface has to copy. One instance reused: only the frame just
    // returned may read it, and that is the frame holding it.
    private var changed: AssChangedRows = AssChangedRows.none()

    /**
     * The pixels, and everything a surface needs to avoid copying all of them.
     *
     * One value rather than three properties read after the fact: a caller that
     * takes the pixels from one frame and the region from the next draws a
     * subtitle half a frame late, and nothing about three separate properties
     * says they may not be mixed.
     */
    public fun render(images: List<AssImage>, frameWidth: Int, frameHeight: Int): AssSurfaceFrame {
        val pixels: IntArray = composite(images, frameWidth, frameHeight)
        return AssSurfaceFrame(pixels, current, generation, changed)
    }

    /**
     * The same as [render], with the blending spread over [bands] strips.
     *
     * Four by default. It is enough to halve the worst frames of an ending
     * sequence and small enough that a television with four slow cores is not
     * asked to schedule more work than it has places to run it — the machines
     * that need this least are the ones a large number would hurt.
     */
    public suspend fun renderParallel(
        images: List<AssImage>,
        frameWidth: Int,
        frameHeight: Int,
        bands: Int = DEFAULT_BANDS,
    ): AssSurfaceFrame {
        val pixels: IntArray = compositeParallel(images, frameWidth, frameHeight, bands)
        return AssSurfaceFrame(pixels, current, generation, changed)
    }

    public fun composite(images: List<AssImage>, frameWidth: Int, frameHeight: Int): IntArray {
        val safeWidth: Int = if (frameWidth > 0) frameWidth else 1
        val safeHeight: Int = if (frameHeight > 0) frameHeight else 1
        resize(safeWidth, safeHeight)

        current = (current + 1) % BUFFER_COUNT
        val pixels: IntArray = buffers[current]
        val stale: AssChangedRows = drawn[current]

        clear(pixels, stale)

        // The union starts as what was cleared, because a surface copying only
        // what was DRAWN keeps the previous occupant of this buffer wherever
        // the new frame does not reach — which on screen is a subtitle that has
        // gone still showing.
        changed.replaceWith(stale)

        stale.reset()
        val whole = Band(stale, top = 0, bottom = safeHeight - 1, palette = serialPalette)
        for (image in images) {
            blit(Surface(pixels, safeWidth, safeHeight), image, whole)
        }
        changed.absorb(stale)

        return pixels
    }

    /**
     * The same frame, composited across [bands] horizontal strips at once.
     *
     * A pixel belongs to exactly one strip and the runs are applied to each
     * strip in the order libass gave them, so the result is the same image the
     * single-threaded path produces — an outline still goes under the glyph it
     * outlines. Parallel over ROWS rather than over runs for that reason:
     * splitting the runs would let two threads blend the same pixel and the
     * order they landed in would be whichever finished first.
     *
     * No Game No Life's ending is the case that needs it. Two hundred and
     * twenty-five runs over an eighth of the screen cost 4ms of blending a
     * frame, and 4ms is a quarter of a 60fps budget spent on subtitles alone.
     */
    public suspend fun compositeParallel(
        images: List<AssImage>,
        frameWidth: Int,
        frameHeight: Int,
        bands: Int,
    ): IntArray {
        if (bands <= 1) return composite(images, frameWidth, frameHeight)

        val safeWidth: Int = if (frameWidth > 0) frameWidth else 1
        val safeHeight: Int = if (frameHeight > 0) frameHeight else 1
        resize(safeWidth, safeHeight)

        current = (current + 1) % BUFFER_COUNT
        val pixels: IntArray = buffers[current]
        val stale: AssChangedRows = drawn[current]

        clear(pixels, stale)
        changed.replaceWith(stale)
        stale.reset()

        // A span set per band, merged after. One shared set would be written by
        // every band at once, and its top and bottom are read-modify-write.
        val strips: Int = minOf(bands, safeHeight)
        val rows: Int = (safeHeight + strips - 1) / strips
        val perBand: Array<AssChangedRows> = bandRows(strips, safeHeight)

        // The caller takes the first strip itself and hands away the rest.
        //
        // A frame ends when its LAST strip does, so every strip that has to be
        // picked up by another thread is one more chance for the frame to wait
        // on a worker the operating system has not scheduled yet. Handing away
        // all eight and idling means the fastest possible frame still costs a
        // dispatch and a wake-up; doing one strip here removes both from the
        // critical path and leaves the caller busy instead of parked.
        spread(Surface(pixels, safeWidth, safeHeight), images, Strips(strips, rows, perBand))

        for (band in perBand) {
            stale.absorb(band)
        }
        changed.absorb(stale)

        return pixels
    }

    // The caller takes the first strip itself and hands away the rest.
    //
    // A frame ends when its LAST strip does, so every strip that has to be
    // picked up by another thread is one more chance for the frame to wait on a
    // worker the operating system has not scheduled yet. Handing away all eight
    // and idling means the fastest possible frame still costs a dispatch and a
    // wake-up; doing one strip here removes both from the critical path and
    // leaves the caller busy instead of parked.
    private suspend fun spread(surface: Surface, images: List<AssImage>, strips: Strips) {
        coroutineScope {
            for (band in 1 until strips.count) {
                val top: Int = band * strips.rows
                val bottom: Int = minOf(top + strips.rows, surface.height) - 1
                if (top > bottom) continue

                launch(Dispatchers.Default) {
                    paint(surface, images, Band(strips.spans[band], top, bottom, palettes[band]))
                }
            }

            val firstBottom: Int = minOf(strips.rows, surface.height) - 1
            if (firstBottom >= 0) {
                paint(surface, images, Band(strips.spans[0], 0, firstBottom, palettes[0]))
            }
        }
    }

    private fun paint(surface: Surface, images: List<AssImage>, band: Band) {
        for (image in images) blit(surface, image, band)
    }

    private var bandSpans: Array<AssChangedRows> = emptyArray()
    private var palettes: Array<RunPalette> = emptyArray()

    private fun bandRows(strips: Int, frameHeight: Int): Array<AssChangedRows> {
        if (bandSpans.size != strips || bandSpans.firstOrNull()?.capacity != frameHeight) {
            bandSpans = Array(strips) { AssChangedRows.forHeight(frameHeight) }
            palettes = Array(strips) { RunPalette() }
        }
        for (band in bandSpans) band.reset()
        return bandSpans
    }

    private fun sized(frameWidth: Int, frameHeight: Int): Boolean = frameWidth == width && frameHeight == height

    private fun resize(frameWidth: Int, frameHeight: Int) {
        if (buffers.isNotEmpty() && sized(frameWidth, frameHeight)) return

        width = frameWidth
        height = frameHeight
        buffers = Array(BUFFER_COUNT) { IntArray(frameWidth * frameHeight) }
        drawn = Array(BUFFER_COUNT) { AssChangedRows.forHeight(frameHeight) }
        changed = AssChangedRows.forHeight(frameHeight)
        current = 0
        // A surface holding a copy of the old size has to replace all of it,
        // not the band the last frame happened to touch. Counted rather than
        // compared: a resize that came back to the same dimensions is still a
        // different buffer.
        generation++
    }

    // Only the columns each row actually holds. A subtitle occupies a band at
    // the bottom of the screen far more often than it occupies the screen, and
    // clearing the whole buffer to find that out is the cost this exists to
    // avoid.
    private fun clear(pixels: IntArray, region: AssChangedRows) {
        if (region.isEmpty) return
        for (y in region.top..region.bottom) {
            val left: Int = region.leftAt(y)
            if (left > region.rightAt(y)) continue
            val start: Int = y * width
            pixels.fill(0, start + left, start + region.rightAt(y) + 1)
        }
    }

    private fun blit(surface: Surface, image: AssImage, band: Band) {
        // Nothing to draw, or a run whose declared rectangle is larger than the
        // bytes that arrived. Reading past the array would be an exception per
        // frame rather than a missing glyph.
        if (image.width <= 0 || image.pixels.size < image.stride * image.height) return

        val colour: Int = assArgbOf(image.colour)
        val tintAlpha: Int = (colour ushr ALPHA_SHIFT) and BYTE_MASK
        if (tintAlpha == 0) return

        val area: Area = clip(surface, image, band) ?: return
        val table: IntArray = band.palette.of(colour, tintAlpha)

        for (y in area.top..area.bottom) {
            blitRow(surface, Run(image, table), y, area)
            band.written.include(y, area.left, area.right)
        }
    }

    // One row of one run. The inner loop is where every pixel of every frame is
    // spent, so it is kept flat: a coverage lookup, a zero test and a blend.
    private fun blitRow(surface: Surface, run: Run, row: Int, area: Area) {
        val image: AssImage = run.image
        val table: IntArray = run.table
        val sourceRow: Int = (row - image.y) * image.stride - image.x
        val targetRow: Int = row * surface.width

        for (x in area.left..area.right) {
            // Zero coverage reads back as a zero entry, so the two cases that
            // used to be skipped separately are one test.
            val source: Int = table[image.pixels[sourceRow + x].toInt() and BYTE_MASK]
            if (source == 0) continue

            val offset: Int = targetRow + x
            val destination: Int = surface.pixels[offset]
            // Nothing under it, which is most pixels: a glyph and its outline
            // touch, they do not tile.
            surface.pixels[offset] = if (destination == 0) source else over(source, destination)
        }
    }

    // The part of a run that lands on this band of this surface, or null when
    // none of it does — which is most runs for most bands.
    private fun clip(surface: Surface, image: AssImage, band: Band): Area? {
        if (image.height <= 0) return null

        val left: Int = maxOf(image.x, 0)
        val top: Int = maxOf(maxOf(image.y, 0), band.top)
        val right: Int = minOf(image.x + image.width, surface.width) - 1
        val bottom: Int = minOf(minOf(image.y + image.height, surface.height) - 1, band.bottom)

        return if (left > right || top > bottom) null else Area(left, top, right, bottom)
    }

    private val serialPalette = RunPalette()
}

// The buffer being drawn into, and its dimensions.
//
// Together because they are never apart: every one of these was travelling as
// three arguments beside four more describing the band, and a blend function
// with eight parameters is one whose call sites cannot be read.
private class Surface(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
)

// One horizontal strip: the rows it owns, the spans it has written, and the
// colour table it is allowed to refill. The table is per band and never shared
// — two threads refilling one race on the colour it currently holds, and the
// failure is a run drawn in another band's colour with nothing thrown.
private class Band(
    val written: AssChangedRows,
    val top: Int,
    val bottom: Int,
    val palette: RunPalette,
)

// How a frame is divided: how many strips, how many rows each holds, and the
// span set each one writes into.
private class Strips(
    val count: Int,
    val rows: Int,
    val spans: Array<AssChangedRows>,
)

// A run and the colour table it is drawn through, which are never useful apart:
// the table IS this run's colour expanded over every coverage value.
private class Run(
    val image: AssImage,
    val table: IntArray,
)

// The rectangle of a run that actually lands on a band, in surface pixels.
private class Area(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

// One colour expanded to every coverage value it can be drawn at.
//
// A run is thousands of pixels and its colour is constant across all of them,
// so the multiplies that turn a colour and a coverage into a premultiplied
// pixel are done 256 times per run instead of once per pixel.
//
// Refilled rather than allocated: a fresh table per run was a quarter of a
// megabyte a frame at two hundred runs, and the garbage that produced showed up
// as 80ms frames in an otherwise 7ms distribution — a pause rather than a slow
// frame, which is the shape of a subtitle that stutters instead of one that is
// late.
//
// One per band, never shared. Two threads refilling the same table race on the
// colour it currently holds, and the failure is a run drawn in the colour of
// whichever band got there first — wrong pixels, nothing thrown.
internal class RunPalette {

    private val entries = IntArray(BYTE_MASK + 1)
    private var colour: Int = 0
    private var filled: Boolean = false

    // The check is not a micro-optimisation: a line, its outline and its shadow
    // are separate runs, and a lyric is dozens of runs in the same two colours.
    fun of(argb: Int, tintAlpha: Int): IntArray {
        if (filled && argb == colour) return entries

        val red: Int = (argb ushr RED_SHIFT) and BYTE_MASK
        val green: Int = (argb ushr GREEN_SHIFT) and BYTE_MASK
        val blue: Int = argb and BYTE_MASK

        for (coverage in 0..BYTE_MASK) {
            val alpha: Int = div255(coverage * tintAlpha)
            entries[coverage] = if (alpha == 0) {
                0
            } else {
                (alpha shl ALPHA_SHIFT) or
                    (div255(red * alpha) shl RED_SHIFT) or
                    (div255(green * alpha) shl GREEN_SHIFT) or
                    div255(blue * alpha)
            }
        }

        colour = argb
        filled = true
        return entries
    }
}

/**
 * One composited frame, and what a surface has to do about it.
 *
 * [slot] and [generation] together identify the buffer: the compositor rotates
 * between two and replaces both on a resize, so a surface keeping its own copy
 * per buffer needs both to know whether the copy it has belongs to the pixels
 * it is being handed.
 *
 * [changed] is the only part that differs from what that buffer held last time
 * it was handed out. A surface that must convert these pixels into some other
 * arrangement — the desktop turns them into bytes for Skia — converts those
 * rows and leaves the rest of its copy alone. It is only valid until the next
 * frame; the compositor reuses it.
 */
public class AssSurfaceFrame internal constructor(
    public val pixels: IntArray,
    public val slot: Int,
    public val generation: Int,
    public val changed: AssChangedRows,
)

/**
 * Which columns of which rows are not what they were.
 *
 * A span per row rather than one rectangle, because the rectangle enclosing a
 * sign in a top corner and a lyric along the bottom is the whole screen. The
 * box would say every row is dirty when two bands of them are, and the surface
 * copying it would pay for the empty middle on every frame of an opening.
 *
 * Inclusive on both ends. A row whose right is less than its left is a row
 * nothing touched, and rows outside [top]..[bottom] were never touched at all.
 */
public class AssChangedRows internal constructor(
    private val left: IntArray,
    private val right: IntArray,
) {
    public var top: Int = left.size
        private set

    public var bottom: Int = -1
        private set

    public val isEmpty: Boolean get() = top > bottom

    internal val capacity: Int get() = left.size

    public fun leftAt(row: Int): Int = left[row]

    public fun rightAt(row: Int): Int = right[row]

    internal fun reset() {
        // Only the rows that were in use. Clearing all of them would be the
        // full-height pass this class exists to avoid, every frame.
        for (row in top..bottom) {
            left[row] = EMPTY_LEFT
            right[row] = EMPTY_RIGHT
        }
        top = left.size
        bottom = -1
    }

    internal fun include(row: Int, from: Int, to: Int) {
        if (from > to) return
        if (left[row] > right[row]) {
            left[row] = from
            right[row] = to
        } else {
            if (from < left[row]) left[row] = from
            if (to > right[row]) right[row] = to
        }
        if (row < top) top = row
        if (row > bottom) bottom = row
    }

    internal fun replaceWith(other: AssChangedRows) {
        reset()
        absorb(other)
    }

    internal fun absorb(other: AssChangedRows) {
        if (other.isEmpty) return
        for (row in other.top..other.bottom) {
            include(row, other.left[row], other.right[row])
        }
    }

    internal companion object {
        fun forHeight(height: Int): AssChangedRows = AssChangedRows(
            IntArray(height) { EMPTY_LEFT },
            IntArray(height) { EMPTY_RIGHT },
        )

        fun none(): AssChangedRows = forHeight(0)

        private const val EMPTY_LEFT = 1
        private const val EMPTY_RIGHT = 0
    }
}

// Source-over on premultiplied pixels: src + dst * (1 - srcAlpha).
//
// The one compositing rule ASS needs — a glyph's outline is drawn under it and
// its shadow under that — and in premultiplied form it is four multiplies and
// no division at all. The straight-alpha version needed three divides per
// channel per pixel to undo its own premultiply, and a divide is twenty times
// the cost of the multiply beside it.
internal fun over(source: Int, destination: Int): Int {
    val sourceAlpha: Int = (source ushr ALPHA_SHIFT) and BYTE_MASK
    val inverse: Int = BYTE_MASK - sourceAlpha
    if (inverse == 0) return source

    val alpha: Int = sourceAlpha + div255(((destination ushr ALPHA_SHIFT) and BYTE_MASK) * inverse)
    val red: Int = ((source ushr RED_SHIFT) and BYTE_MASK) +
        div255(((destination ushr RED_SHIFT) and BYTE_MASK) * inverse)
    val green: Int = ((source ushr GREEN_SHIFT) and BYTE_MASK) +
        div255(((destination ushr GREEN_SHIFT) and BYTE_MASK) * inverse)
    val blue: Int = (source and BYTE_MASK) + div255((destination and BYTE_MASK) * inverse)

    return (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

// Exact division by 255 for a product of two bytes, without dividing. Verified
// over the whole 0..65025 input range by AssCompositeTest — "close enough"
// rounding here is a glyph edge that is one level too dark on every frame.
internal fun div255(value: Int): Int = (value + 1 + (value shr BYTE_BITS)) shr BYTE_BITS

private const val BUFFER_COUNT = 2
// Eight, measured rather than picked. Four left No Game No Life's ending
// missing 32 frames in 4688 at 60fps; eight missed none, and its worst frame
// went from 35.8ms to 10.7ms. A machine with fewer cores than this just runs
// the strips in turn — the work is the same, only the concurrency changes.
private const val DEFAULT_BANDS = 8
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val RED_BYTE = 24
private const val GREEN_BYTE = 16
private const val BLUE_BYTE = 8
