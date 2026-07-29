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

// Which band a width falls in at the edges, including the widths the browser
// never has to answer for.
//
// A CSS container query always has a number. `BoxWithConstraints` does not: a
// parent that scrolls horizontally hands back an unbounded width, so the port
// has a case its oracle cannot teach it, and the bar's answer to that case has
// to be a decision rather than an accident of Float-to-Int conversion.
class ChromeBreakpointBoundsTest {

    @Test
    fun eachBandClaimsItsOwnCeiling() {
        assertEquals("xs", breakpointFor(320).name)
        assertEquals("sm", breakpointFor(321).name)
        assertEquals("sm", breakpointFor(480).name)
        assertEquals("md", breakpointFor(481).name)
        assertEquals("md", breakpointFor(720).name)
        assertEquals("lg", breakpointFor(721).name)
        assertEquals("lg", breakpointFor(1024).name)
        assertEquals("xl", breakpointFor(1025).name)
    }

    // The number the bar substitutes for an unbounded constraint has to land on
    // the widest band, or "as much room as you want" would draw the narrow bar.
    @Test
    fun anUnboundedWidthLandsOnTheWidestBand() {
        assertEquals("xl", breakpointFor(100_000).name)
    }

    // And the failure the guard exists for. A width that arrived as zero — which
    // is what a NaN constraint converts to — selects the NARROWEST band, so a
    // bar that silently collapsed to one control would look like a layout
    // decision rather than a missing measurement.
    @Test
    fun aZeroWidthWouldCollapseTheBar() {
        assertEquals("xs", breakpointFor(0).name)
        assertEquals(1, breakpointFor(0).hideAfterRank)
    }
}
