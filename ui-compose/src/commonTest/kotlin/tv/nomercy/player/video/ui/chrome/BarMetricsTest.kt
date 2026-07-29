// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

// The four sets of spacing the web has, at the widths it changes them.
//
// These exist because the chrome had one gap and one padding, and the reason it
// looked defensible is that the BASE css rule really does say 2px and 4px 16px.
// The other three sets are container queries further down the same file, and a
// check that read the base declaration would have agreed with the wrong code.
class BarMetricsTest {

    @Test
    fun aWideBarGetsTheWebsWidestSpacing() {
        val wide = barMetricsFor(1600)

        assertEquals(2.dp, wide.gap)
        assertEquals(4.dp, wide.paddingVertical)
        assertEquals(16.dp, wide.paddingHorizontal)
    }

    // The gap goes to ZERO here, not to one. A player embedded in a sidebar hits
    // this while the window around it is still wide, which is why it is the
    // container's width and not the viewport's.
    @Test
    fun atSevenTwentyTheGapClosesAndThePaddingHalves() {
        val md = barMetricsFor(720)

        assertEquals(0.dp, md.gap)
        assertEquals(8.dp, md.paddingHorizontal)
        assertEquals(2.dp, barMetricsFor(721).gap, "721 is past the query and keeps the wide gap")
    }

    @Test
    fun atFourEightyThePaddingHalvesAgain() {
        assertEquals(4.dp, barMetricsFor(480).paddingHorizontal)
        assertEquals(8.dp, barMetricsFor(481).paddingHorizontal, "481 is past the query")
    }

    // The only tier that touches the vertical padding, which is what makes a
    // 40dp row fit on a phone at all.
    @Test
    fun atThreeSixtyEvenTheVerticalPaddingGivesWay() {
        val xs = barMetricsFor(360)

        assertEquals(2.dp, xs.paddingVertical)
        assertEquals(2.dp, xs.paddingHorizontal)
        assertEquals(4.dp, barMetricsFor(361).paddingVertical, "361 is past the query")
    }

    // What the single constant cost: at the narrowest width the old padding was
    // 16 a side against the web's 2, which is 28dp of a 360dp bar spent on
    // nothing — most of a control.
    @Test
    fun theNarrowBarKeepsTheRoomTheOldConstantSpent() {
        val spentBefore = 16.dp * 2
        val spentNow = barMetricsFor(360).paddingHorizontal * 2

        assertEquals(4.dp, spentNow)
        assertEquals(32.dp, spentBefore)
    }
}
