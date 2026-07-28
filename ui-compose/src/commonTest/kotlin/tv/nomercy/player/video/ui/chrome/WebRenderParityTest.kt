// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The side-by-side check, control by control, at the same width.
 *
 * Every expectation below was MEASURED. `e2e/render-parity-export.spec.ts` in
 * the web player drives the real chrome at each breakpoint boundary and writes
 * down what the browser lays out; this is that file, transcribed once, and the
 * export regenerates it.
 *
 * Compared as sets. The bar draws in layout order and the rules walk in
 * priority order, and the two differ — previous sits left of mute on screen and
 * after it in the list. Comparing sequences would fail for a reason that is not
 * drift.
 *
 * The fixture is a bare MP4 in a single-item queue, so three controls are gated
 * by content rather than by width, and one has no element on the web at all.
 */
class WebRenderParityTest {

    // `volume` is a rank with no control behind it: `initButtonMap` maps `mute`
    // to the volume element and `volume` to null. And sample.mp4 has no
    // chapters, so both chapter jumps are content-gated.
    private val fixtureGating: (ChromeControl) -> Boolean = {
        it == ChromeControl.VOLUME ||
            it == ChromeControl.CHAPTER_PREV ||
            it == ChromeControl.CHAPTER_NEXT
    }

    private fun native(width: Int, portrait: Boolean = false): Set<ChromeControl> =
        visibleControls(width, portrait = portrait, contentHidden = fixtureGating).toSet()

    private val web: List<Pair<Int, List<ChromeControl>>> = listOf(
        320 to listOf(
            ChromeControl.PLAY,
            ChromeControl.NEXT,
            ChromeControl.SETTINGS,
            ChromeControl.FULLSCREEN,
        ),
        321 to listOf(
            ChromeControl.PLAY,
            ChromeControl.NEXT,
            ChromeControl.SETTINGS,
            ChromeControl.FULLSCREEN,
        ),
        480 to listOf(
            ChromeControl.PLAY,
            ChromeControl.NEXT,
            ChromeControl.MUTE,
            ChromeControl.SETTINGS,
            ChromeControl.FULLSCREEN,
        ),
        481 to listOf(
            ChromeControl.PLAY,
            ChromeControl.NEXT,
            ChromeControl.MUTE,
            ChromeControl.SETTINGS,
            ChromeControl.FULLSCREEN,
        ),
        720 to ALL_SIX,
        721 to ALL_SIX,
        1024 to ALL_SIX,
        1025 to ALL_SIX,
        1440 to ALL_SIX,
    )

    @Test
    fun theBarMatchesTheBrowserAtEveryBreakpointBoundary() {
        web.forEach { (width, expected) ->
            assertEquals(
                expected.toSet(),
                native(width),
                "at ${width}dp the native bar differs from what the browser draws",
            )
        }
    }

    // Sampled wide enough that nothing is dropped for want of room, so this
    // records the orientation rule on its own rather than tangled with the fit.
    @Test
    fun portraitMatchesTheBrowserToo() {
        assertEquals(
            setOf(
                ChromeControl.PLAY,
                ChromeControl.MUTE,
                ChromeControl.SETTINGS,
                ChromeControl.FULLSCREEN,
            ),
            native(900, portrait = true),
            "portrait drops a different set than the browser does",
        )
    }

    // The boundaries carry no step, which is the finding this suite exists to
    // hold. An earlier export appeared to show previous and next arriving at
    // 721 and that read as a band edge; it was the viewport turning portrait at
    // every width up to its own height. Width alone changes nothing at 720.
    @Test
    fun nothingHappensAtTheBandEdges() {
        listOf(320 to 321, 480 to 481, 720 to 721, 1024 to 1025).forEach { (below, above) ->
            assertEquals(
                native(below),
                native(above),
                "a band edge changed the bar between $below and $above",
            )
        }
    }
}

private val ALL_SIX = listOf(
    ChromeControl.PLAY,
    ChromeControl.PREVIOUS,
    ChromeControl.NEXT,
    ChromeControl.MUTE,
    ChromeControl.SETTINGS,
    ChromeControl.FULLSCREEN,
)
