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

// Which rungs a viewer is offered, against the browser reading the same file.
//
// Cosmos Laundromat's master declares eight variants and two of them are PQ:
// 1920x804 PQ, 1920x804 SDR, 2048x858 PQ. The browser drops the PQ ones on a
// panel that cannot render them, because it would decode them and map the
// colours back down - a washed-out picture the viewer chose. Nothing filtered
// here, so the desktop list offered "1920x804 HDR10" on an SDR display.
class OfferableRungsTest {

    private fun rung(width: Int, height: Int, range: DynamicRange): QualityLevel =
        QualityLevel(height = height, bitrate = 0, codec = "avc1", dynamicRange = range, width = width)

    private val cosmos = listOf(
        rung(1280, 536, DynamicRange.SDR),
        rung(1920, 804, DynamicRange.HDR10),
        rung(1920, 804, DynamicRange.SDR),
        rung(2048, 858, DynamicRange.HDR10),
    )

    @Test
    fun anSdrDisplayIsOfferedNoHdrRung() {
        val offered = offerableRungs(cosmos, hdrDisplay = false)

        assertEquals(listOf(536, 804), offered.map { it.height })
        assertEquals(emptyList(), offered.filter { it.dynamicRange != DynamicRange.SDR })
    }

    @Test
    fun andAnHdrDisplayIsOfferedEveryOne() {
        assertEquals(cosmos.size, offerableRungs(cosmos, hdrDisplay = true).size)
    }

    @Test
    fun theSdrRungAtAHeightSharedWithAnHdrOneSurvives() {
        // Both 1920x804 variants are real and only one is playable here. A
        // filter keyed on height rather than range would take the wrong one.
        val offered = offerableRungs(cosmos, hdrDisplay = false)

        assertEquals(1920, offered.last().width)
        assertEquals(DynamicRange.SDR, offered.last().dynamicRange)
    }
}
