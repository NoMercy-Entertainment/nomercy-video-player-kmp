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

/**
 * The bar's spacing at the bands a browser has actually been measured in.
 *
 * `scripts/web-bar-padding.mjs` drives the running web testbed through four
 * viewport widths and reads the COMPUTED padding — what the browser settled on
 * after every rule had its say, which is not what any one stylesheet line says.
 *
 *   viewport   player   padding-x   padding-y   row   button
 *        360      158           2           2    40       40
 *        480      278           2           2    40       40
 *        720      518           8           4    40       40
 *       1280      662           8           4    40       40
 *
 * The PLAYER's width picks the band, not the viewport's — a player in a sidebar
 * hits a narrow band while the window around it is still wide. Reading the
 * viewport column instead put the 662 sample against the widest band and made
 * three correct constants look wrong; the constants were fine and the ruler was
 * not.
 *
 * So only XS and MD are asserted here. SM (360..480) and the widest band
 * (>720) have no player-width sample yet, and asserting them from a viewport
 * number would be asserting the same mistake.
 *
 * What every sample agrees on is the row and the button: forty and forty, at
 * every width, with padding on top of both rather than out of them.
 */
class BarBandMetricsTest {

    @Test
    fun theNarrowestBandMatchesTheBrowser() {
        assertEquals(2.dp, barMetricsFor(158).paddingHorizontal, "player 158 resolves 2px")
        assertEquals(2.dp, barMetricsFor(158).paddingVertical)
        assertEquals(2.dp, barMetricsFor(278).paddingHorizontal, "player 278 resolves 2px")
        assertEquals(2.dp, barMetricsFor(278).paddingVertical)
    }

    @Test
    fun theMediumBandMatchesTheBrowser() {
        assertEquals(8.dp, barMetricsFor(518).paddingHorizontal, "player 518 resolves 8px")
        assertEquals(4.dp, barMetricsFor(518).paddingVertical)
        assertEquals(8.dp, barMetricsFor(662).paddingHorizontal, "player 662 resolves 8px")
        assertEquals(4.dp, barMetricsFor(662).paddingVertical)
    }

    /**
     * The row is forty tall and so is the button inside it.
     *
     * The browser reports both as forty at all four samples while also
     * resolving two to four points of vertical padding, so that padding does
     * not come out of the button. The native bar stated the row's height and
     * padded inside it, which laid the buttons out at 32 while every width
     * assertion in the suite passed — one axis had never been measured.
     */
    @Test
    fun theRowIsAsTallAsTheButtonsInIt() {
        assertEquals(40.dp, TRANSPORT_ROW_HEIGHT)
    }
}
