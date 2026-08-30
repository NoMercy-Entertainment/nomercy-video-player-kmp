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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AssCompositeTest {

    // libass colour 0xRRGGBBAA with INVERSE alpha in the last byte.
    private fun libassColour(red: Int, green: Int, blue: Int, alpha: Int): Int =
        (red shl 24) or (green shl 16) or (blue shl 8) or (255 - alpha)

    // Where a run sits, apart from what it is filled with — the two halves were
    // travelling as six positional arguments and every call site read as a row
    // of unlabelled numbers.
    private class At(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun run(at: At, colour: Int, coverage: Int): AssImage = AssImage(
        x = at.x,
        y = at.y,
        width = at.width,
        height = at.height,
        stride = at.width,
        colour = colour,
        pixels = ByteArray(at.width * at.height) { coverage.toByte() },
    )

    @Test
    fun divideBy255IsExactAcrossEveryProductOfTwoBytes() {
        for (value in 0..(255 * 255)) {
            assertEquals(value / 255, div255(value), "div255($value)")
        }
    }

    @Test
    fun anOpaqueRunIsWrittenAtFullColour() {
        val pixels: IntArray = compositeAssFrame(
            listOf(run(At(0, 0, 2, 2), libassColour(0x40, 0x80, 0xC0, 255), 255)),
            2,
            2,
        )

        assertEquals(0xFF4080C0.toInt(), pixels[0])
    }

    // The whole point of the change: the toolkit is handed pixels already
    // multiplied by their alpha. Half-covered white is half-grey, not white.
    @Test
    fun aHalfCoveredRunIsPremultiplied() {
        val pixels: IntArray = compositeAssFrame(
            listOf(run(At(0, 0, 1, 1), libassColour(0xFF, 0xFF, 0xFF, 255), 128)),
            1,
            1,
        )

        val alpha: Int = (pixels[0] ushr 24) and 0xFF
        val red: Int = (pixels[0] ushr 16) and 0xFF
        assertEquals(128, alpha)
        assertEquals(alpha, red, "premultiplied white has colour equal to its alpha")
    }

    @Test
    fun aFullyTransparentRunDrawsNothing() {
        val pixels: IntArray = compositeAssFrame(
            listOf(run(At(0, 0, 2, 2), libassColour(0xFF, 0x00, 0x00, 0), 255)),
            2,
            2,
        )

        assertTrue(pixels.all { it == 0 })
    }

    // Runs arrive back-to-front. An outline drawn over the glyph it outlines is
    // the visible failure of getting this backwards.
    @Test
    fun aLaterRunCoversAnEarlierOne() {
        val pixels: IntArray = compositeAssFrame(
            listOf(
                run(At(0, 0, 1, 1), libassColour(0xFF, 0x00, 0x00, 255), 255),
                run(At(0, 0, 1, 1), libassColour(0x00, 0xFF, 0x00, 255), 255),
            ),
            1,
            1,
        )

        assertEquals(0xFF00FF00.toInt(), pixels[0])
    }

    @Test
    fun aRunReachingPastTheSurfaceIsClippedRatherThanThrowing() {
        val pixels: IntArray = compositeAssFrame(
            listOf(run(At(-1, -1, 4, 4), libassColour(0xFF, 0xFF, 0xFF, 255), 255)),
            2,
            2,
        )

        assertTrue(pixels.all { it == 0xFFFFFFFF.toInt() })
    }

    @Test
    fun aRunDeclaringMoreRowsThanItCarriesIsSkipped() {
        val short = AssImage(
            x = 0,
            y = 0,
            width = 4,
            height = 4,
            stride = 4,
            colour = libassColour(0xFF, 0xFF, 0xFF, 255),
            pixels = ByteArray(4),
        )

        assertTrue(compositeAssFrame(listOf(short), 4, 4).all { it == 0 })
    }

    // The buffer is reused, so the frame before it has to be gone. It was not
    // possible to get this wrong when every frame allocated.
    @Test
    fun aReusedCompositorDoesNotLeakThePreviousFrame() {
        val compositor = AssFrameCompositor()
        val white: Int = libassColour(0xFF, 0xFF, 0xFF, 255)

        compositor.composite(listOf(run(At(0, 0, 4, 4), white, 255)), 8, 8)
        // Three frames, because the buffers rotate: the second frame writes the
        // other buffer and only the third comes back to the one the first used.
        compositor.composite(listOf(run(At(4, 4, 4, 4), white, 255)), 8, 8)
        val third: IntArray = compositor.composite(listOf(run(At(4, 4, 4, 4), white, 255)), 8, 8)

        assertEquals(0, third[0], "the first frame's glyph is still in the buffer")
        assertEquals(0xFFFFFFFF.toInt(), third[4 * 8 + 4])
    }

    // The frame handed out last is on screen and being drawn from. Writing the
    // next one into it repaints a frame the toolkit already owns.
    @Test
    fun consecutiveFramesAreDifferentBuffers() {
        val compositor = AssFrameCompositor()
        val white: Int = libassColour(0xFF, 0xFF, 0xFF, 255)

        val first: IntArray = compositor.composite(listOf(run(At(0, 0, 2, 2), white, 255)), 4, 4)
        val second: IntArray = compositor.composite(emptyList(), 4, 4)

        assertNotEquals(0, first[0], "the frame handed out earlier was cleared underneath its owner")
        assertTrue(second.all { it == 0 })
    }

    // A surface copying only what was DRAWN keeps the previous occupant of the
    // buffer wherever the new frame does not reach, which on screen is a
    // subtitle that has gone still showing.
    @Test
    fun theChangedRegionCoversWhatWasClearedAsWellAsWhatWasDrawn() {
        val compositor = AssFrameCompositor()
        val white: Int = libassColour(0xFF, 0xFF, 0xFF, 255)

        compositor.render(listOf(run(At(0, 0, 2, 2), white, 255)), 16, 16)
        compositor.render(emptyList(), 16, 16)
        // Back to the buffer the first frame used, drawing somewhere else.
        val third: AssSurfaceFrame = compositor.render(listOf(run(At(10, 10, 2, 2), white, 255)), 16, 16)

        assertEquals(0, third.changed.top, "the rows the first frame dirtied were left out")
        assertEquals(11, third.changed.bottom)
        assertEquals(0, third.changed.leftAt(0))
        assertEquals(1, third.changed.rightAt(0))
        assertEquals(10, third.changed.leftAt(10))
        assertEquals(11, third.changed.rightAt(10))
    }

    // The whole reason the region is a span per row. A sign in one corner and a
    // lyric in the other are enclosed by the entire screen, and a surface
    // copying that rectangle pays for the empty middle on every frame.
    @Test
    fun twoDistantRunsLeaveTheRowsBetweenThemUntouched() {
        val compositor = AssFrameCompositor()
        val white: Int = libassColour(0xFF, 0xFF, 0xFF, 255)

        val frame: AssSurfaceFrame = compositor.render(
            listOf(run(At(0, 0, 2, 2), white, 255), run(At(60, 60, 2, 2), white, 255)),
            64,
            64,
        )

        assertEquals(0, frame.changed.top)
        assertEquals(61, frame.changed.bottom)
        assertTrue(
            frame.changed.leftAt(30) > frame.changed.rightAt(30),
            "a row with nothing on it was reported as changed",
        )
    }

    // A row's span is the extent of everything on it, not of the last thing.
    @Test
    fun aRowTouchedByTwoRunsSpansBoth() {
        val compositor = AssFrameCompositor()
        val white: Int = libassColour(0xFF, 0xFF, 0xFF, 255)

        val frame: AssSurfaceFrame = compositor.render(
            listOf(run(At(2, 0, 2, 1), white, 255), run(At(20, 0, 2, 1), white, 255)),
            32,
            32,
        )

        assertEquals(2, frame.changed.leftAt(0))
        assertEquals(21, frame.changed.rightAt(0))
    }

    @Test
    fun aFrameThatDrewNothingIntoACleanBufferReportsNoChangedRegion() {
        val compositor = AssFrameCompositor()

        compositor.render(emptyList(), 8, 8)
        val second: AssSurfaceFrame = compositor.render(emptyList(), 8, 8)

        assertTrue(second.changed.isEmpty)
    }

    @Test
    fun consecutiveFramesReportDifferentSlots() {
        val compositor = AssFrameCompositor()

        val first: AssSurfaceFrame = compositor.render(emptyList(), 8, 8)
        val second: AssSurfaceFrame = compositor.render(emptyList(), 8, 8)

        assertNotEquals(first.slot, second.slot)
    }

    // A surface keeping its own copy per buffer has to throw it away on a
    // resize. Comparing sizes would miss a resize that came back to the same
    // one.
    @Test
    fun aResizeAdvancesTheGeneration() {
        val compositor = AssFrameCompositor()

        val before: AssSurfaceFrame = compositor.render(emptyList(), 8, 8)
        val other: AssSurfaceFrame = compositor.render(emptyList(), 16, 16)
        val back: AssSurfaceFrame = compositor.render(emptyList(), 8, 8)

        assertNotEquals(before.generation, other.generation)
        assertNotEquals(other.generation, back.generation)
    }

    // The parallel path exists to be faster, and it is only allowed to be
    // faster if it draws the same thing. Overlapping runs in different colours
    // straddling several band boundaries is the case that catches a band
    // applying runs out of order or sharing a colour table with its neighbour.
    @Test
    fun compositingInBandsDrawsTheSamePixelsAsCompositingInOnePass() = runTest {
        val images: List<AssImage> = listOf(
            run(At(0, 0, 40, 40), libassColour(0xFF, 0x00, 0x00, 255), 200),
            run(At(10, 8, 40, 40), libassColour(0x00, 0xFF, 0x00, 180), 255),
            run(At(5, 20, 50, 30), libassColour(0x00, 0x00, 0xFF, 120), 90),
            run(At(30, 1, 20, 60), libassColour(0xFF, 0xFF, 0x00, 255), 40),
        )

        val serial: IntArray = AssFrameCompositor().composite(images, 64, 64).copyOf()
        val parallel: IntArray = AssFrameCompositor().compositeParallel(images, 64, 64, bands = 8)

        assertTrue(serial.contentEquals(parallel), "the banded frame differs from the single-pass frame")
    }

    @Test
    fun compositingInBandsReportsTheSameChangedRows() = runTest {
        val images: List<AssImage> = listOf(
            run(At(2, 3, 8, 8), libassColour(0xFF, 0xFF, 0xFF, 255), 255),
            run(At(40, 50, 8, 8), libassColour(0xFF, 0x00, 0x00, 255), 255),
        )

        val serial: AssSurfaceFrame = AssFrameCompositor().render(images, 64, 64)
        val banded = AssFrameCompositor()
        banded.compositeParallel(images, 64, 64, bands = 8)
        val parallel: AssSurfaceFrame = banded.render(images, 64, 64)

        assertEquals(serial.changed.top, parallel.changed.top)
        assertEquals(serial.changed.bottom, parallel.changed.bottom)
        assertEquals(serial.changed.leftAt(5), parallel.changed.leftAt(5))
        assertEquals(serial.changed.rightAt(55), parallel.changed.rightAt(55))
    }

    @Test
    fun aResizedSurfaceStartsFromACleanBuffer() {
        val compositor = AssFrameCompositor()
        val white: Int = libassColour(0xFF, 0xFF, 0xFF, 255)

        compositor.composite(listOf(run(At(0, 0, 4, 4), white, 255)), 8, 8)
        val resized: IntArray = compositor.composite(emptyList(), 16, 16)

        assertEquals(16 * 16, resized.size)
        assertTrue(resized.all { it == 0 })
    }

    @Test
    fun twoLoopsHandingOverThroughAMutexEachGetAWholeFrame() = runTest(timeout = 60.seconds) {
        // The phone's crash was a resize starting a second rasterising loop
        // while the first was still inside a blend — cancellation cannot land
        // until the blend returns — and both then drove one compositor.
        //
        // This is the contract that fixes it: the layer serialises, and the
        // compositor is correct for a handover on a DIFFERENT thread from the
        // one that sized it. Racing them unserialised is not the contract and
        // is not tested here; the compositor does not offer it, and asserting
        // it produced a test that failed roughly one run in eight.
        val white: Int = libassColour(0xFF, 0xFF, 0xFF, 255)
        val images: List<AssImage> = listOf(run(At(0, 0, 8, 8), white, 255))
        val turn = Mutex()

        repeat(200) {
            val compositor = AssFrameCompositor()
            val sizes: MutableList<Int> = mutableListOf()
            coroutineScope {
                repeat(2) {
                    launch(Dispatchers.Default) {
                        val frame: IntArray = turn.withLock {
                            compositor.compositeParallel(images, 64, 64, bands = 4)
                        }
                        turn.withLock { sizes.add(frame.size) }
                    }
                }
            }
            assertEquals(listOf(64 * 64, 64 * 64), sizes, "a frame came back the wrong size")
        }
    }
}
