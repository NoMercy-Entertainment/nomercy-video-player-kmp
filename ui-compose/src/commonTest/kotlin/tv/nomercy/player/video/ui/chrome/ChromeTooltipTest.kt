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

// The arithmetic from desktop-ui/helpers/tooltips.ts, at the edges where it
// matters. A tooltip centred in the middle of a wide bar is the case that
// cannot go wrong; the first and last buttons are the ones that push a label
// off the picture.
class ChromeTooltipTest {

    private val left = 0f
    private val right = 1000f

    @Test
    fun aTooltipInTheMiddleIsSimplyCentred() {
        assertEquals(460f, ChromeTooltip.leftFor(500f, 80f, left, right))
        assertEquals(0f, ChromeTooltip.arrowShift(500f, 80f, left, right))
    }

    @Test
    fun theFirstButtonKeepsItsLabelInsideTheBar() {
        // Centred would put it at -30. The bar starts at 0.
        assertEquals(0f, ChromeTooltip.leftFor(10f, 80f, left, right))
    }

    @Test
    fun theLastButtonKeepsItsLabelInsideTheBar() {
        // Centred would end at 1030. The bar ends at 1000.
        assertEquals(920f, ChromeTooltip.leftFor(990f, 80f, left, right))
    }

    // The whole reason the web carries an --arrow-x: the label moved, so an
    // arrow drawn at a fixed centre would point at the button next door.
    @Test
    fun aClampedLabelReportsHowFarItMoved() {
        assertEquals(30f, ChromeTooltip.arrowShift(10f, 80f, left, right))
        assertEquals(-30f, ChromeTooltip.arrowShift(990f, 80f, left, right))
    }

    // A label wider than the bar has to hang off one edge. The web picks the
    // left, and picking the other one is a difference nobody would notice until
    // a long translation arrived.
    @Test
    fun aLabelWiderThanTheBarHangsOffTheRight() {
        assertEquals(0f, ChromeTooltip.leftFor(500f, 1200f, left, right))
    }

    @Test
    fun theDelayIsTheWebsHalfSecond() {
        assertEquals(500L, ChromeTooltip.DELAY_MS)
    }
}
