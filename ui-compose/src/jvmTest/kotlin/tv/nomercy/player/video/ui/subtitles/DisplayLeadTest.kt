// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayLeadTest {

    @Test
    fun beforeAnyMeasurementThereIsNoLead() {
        assertEquals(0L, DisplayLead().leadMs(moving = true))
    }

    @Test
    fun aMovingVideoIsDrawnForWhereItWillBeWhenThePictureIsShown() {
        val lead = DisplayLead()
        repeat(SETTLE) {
            lead.observeRender(RENDER_MS)
            lead.observeFrame(FRAME_MS)
        }

        assertEquals(RENDER_MS + FRAME_MS, lead.leadMs(moving = true).toDouble(), 1.0)
    }

    @Test
    fun aPausedVideoIsDrawnForTheFrameThatIsOnScreen() {
        val lead = DisplayLead()
        repeat(SETTLE) {
            lead.observeRender(RENDER_MS)
            lead.observeFrame(FRAME_MS)
        }

        assertEquals(0L, lead.leadMs(moving = false))
    }

    @Test
    fun aGapBetweenFramesIsNotTakenForTheRefreshInterval() {
        val lead = DisplayLead()
        repeat(SETTLE) { lead.observeFrame(FRAME_MS) }
        lead.observeFrame(2_000.0)

        assertEquals(FRAME_MS, lead.leadMs(moving = true).toDouble(), 1.0)
    }
}

private const val SETTLE = 60
private const val RENDER_MS = 12.0
private const val FRAME_MS = 16.6
