// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.ports.DynamicRange
import tv.nomercy.player.core.ports.QualityLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The labels that carry a value.
//
// iconStateMethods.ts does two things per control and only the glyph swap was ported.
// Every control announced a static noun, so the speed button said "Speed" at 1.5× and
// the quality button said "Quality" while playing 1080p. Invisible as an a11y gap
// until tooltips landed and started reading the same string.
class ControlLabelsTest {

    @Test
    fun theRateIsNamedOnlyWhenItIsNotNormal() {
        // The web's condition. "Speed (1×)" on ordinary playback is noise, and a
        // screen reader reads it on every focus.
        assertEquals("Speed", speedLabel("Speed", 1f))
        assertEquals("Speed (1.5×)", speedLabel("Speed", 1.5f))
    }

    @Test
    fun awholeRateDropsItsDecimal() {
        // JS prints `2×`; Kotlin's Float gives "2.0" without help. A parity check on
        // the string would catch it and a human would call it a typo.
        assertEquals("Speed (2×)", speedLabel("Speed", 2f))
        assertEquals("Speed (0.5×)", speedLabel("Speed", 0.5f))
    }

    @Test
    fun theMultiplicationSignIsNotTheLetterX() {
        assertEquals(true, speedLabel("Speed", 2f).contains("×"))
        assertEquals(false, speedLabel("Speed", 2f).contains("x"))
    }

    @Test
    fun theQualityLabelNamesWhatIsPlaying() {
        assertEquals("Quality: 1080p", qualityLabel("Quality", "1080p"))
    }

    @Test
    fun anUnknownQualityLeavesTheLabelAlone() {
        // Before the first level-switched lands, and on any non-adaptive source.
        // "Quality: null" or a trailing colon would both be worse than the noun.
        assertEquals("Quality", qualityLabel("Quality", null))
    }

    @Test
    fun aRungPrefersItsOwnLabel() {
        // A server that names a rung "1080p HDR" means it.
        assertEquals("1080p HDR", rung(height = 1080, label = "1080p HDR").describe())
        assertEquals("720p", rung(height = 720, label = null).describe())
    }

    @Test
    fun aRungWithNoHeightAndNoLabelDescribesNothing() {
        // An audio-only rendition, or a ladder a backend has only half described.
        // Returning "0p" would put a nonsense value in the button's label.
        assertNull(rung(height = 0, label = null).describe())
    }
}

private fun rung(height: Int, label: String?): QualityLevel = QualityLevel(
    height = height,
    bitrate = 3_000_000,
    codec = "h264",
    dynamicRange = DynamicRange.SDR,
    label = label,
)
