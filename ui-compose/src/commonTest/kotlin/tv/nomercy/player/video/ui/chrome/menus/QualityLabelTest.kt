// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import tv.nomercy.player.core.ports.DynamicRange
import tv.nomercy.player.core.ports.QualityLevel
import kotlin.test.Test
import kotlin.test.assertEquals

// What a quality row says, against the browser showing the same ladder.
//
// Photographed side by side on Cosmos Laundromat: the native pane read
// "108p / 178p / 268p / 358p / 536p / 804p hdr10" where the browser read
// "256x108 SDR / 426x178 SDR / 640x268 SDR / 854x358 SDR / 1280x536 SDR /
// 1920x804 SDR". A height on its own is not a rung anyone recognises - 536p is
// not a number a viewer has ever seen - and it is the same stream the browser
// names 1280x536.
class QualityLabelTest {

    private fun level(width: Int?, height: Int, range: DynamicRange = DynamicRange.SDR): QualityLevel =
        QualityLevel(height = height, bitrate = 0, codec = "avc1", dynamicRange = range, width = width)

    @Test
    fun aRungIsNamedByBothOfItsDimensions() {
        assertEquals("1280x536 SDR", qualityLabel(level(1280, 536)))
    }

    @Test
    fun andTheRangeIsShownOnEveryRowRatherThanOnlyTheUnusualOnes() {
        // SDR was suppressed, so a ladder of ordinary rungs carried no range at
        // all and the one HDR row looked like a different kind of entry.
        assertEquals("1920x804 HDR10", qualityLabel(level(1920, 804, DynamicRange.HDR10)))
    }

    @Test
    fun aRungThatDeclaresNoWidthStillNamesItsHeight() {
        // A container track can report a height and no width. Dropping the row
        // would hide a rung a viewer can actually pick.
        assertEquals("536p SDR", qualityLabel(level(null, 536)))
    }
}
