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
import tv.nomercy.player.video.ui.chrome.ChromeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The Auto row, against the browser showing the same ladder.
//
// The browser reads "Automatisch  1280x536 SDR" with the mark on that row. The
// native pane read "Automatisch" with the mark on 108p - the lowest rung the
// ladder happened to open on - because the check was keyed on which rung is
// DECODING and an adaptive engine always reports one. So a viewer in Auto was
// told they had picked the worst rung, and never saw which one was playing.
class AutoQualityRowTest {

    private val playing = QualityLevel(
        height = 536,
        bitrate = 1_000_000,
        codec = "avc1",
        dynamicRange = DynamicRange.SDR,
        width = 1280,
    )

    private val strings = menuStrings("nl")

    @Test
    fun autoNamesTheRungTheEngineSettledOn() {
        val state = ChromeState(activeQuality = playing, qualityAuto = true)

        assertEquals("${strings.automatic}  1280x536 SDR", autoQualityLabel(state, strings))
    }

    @Test
    fun andSaysNothingExtraWhenNoRungIsDecodingYet() {
        val state = ChromeState(activeQuality = null, qualityAuto = true)

        assertEquals(strings.automatic, autoQualityLabel(state, strings))
    }

    @Test
    fun aChosenRungTakesTheSublabelBackOffAuto() {
        // Out of Auto the row is the mode again, and the rung wears its own mark.
        val state = ChromeState(activeQuality = playing, qualityAuto = false)

        assertEquals(strings.automatic, autoQualityLabel(state, strings))
        assertTrue(!state.qualityAuto)
    }
}
