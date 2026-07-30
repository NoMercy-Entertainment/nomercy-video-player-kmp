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

// The volume control's two decisions: which slider, and how wide.
//
// There was no slider. The bar drew a mute button and visibleControls reserved
// CHROME_VOLUME_SLIDER_WIDTH beside it — 96dp, the web's 80px track plus its two
// 8px margins — and nothing was ever drawn in that space, so a viewer could mute
// and could not turn it down. setVolume was on ChromeCommands with a working
// implementation behind it and no caller.
class VolumeControlTest {

    // `.volume-slider` opens to 80px, then 60, 48 and 32 as the container crosses
    // 720, 480 and 360 — the same breakpoints the bar's spacing uses. A track that
    // stayed 80 wide on a narrow player pushes the clocks off the end of the row.
    @Test
    fun theTrackNarrowsWithThePlayer() {
        assertEquals(80.dp, expandedWidthFor(1280))
        assertEquals(80.dp, expandedWidthFor(721))
        assertEquals(60.dp, expandedWidthFor(720))
        assertEquals(60.dp, expandedWidthFor(481))
        assertEquals(48.dp, expandedWidthFor(480))
        assertEquals(48.dp, expandedWidthFor(361))
        assertEquals(32.dp, expandedWidthFor(360))
        assertEquals(32.dp, expandedWidthFor(200))
    }

    // The reservation and the track have to agree, or the row either overflows or
    // leaves a gap. 80 + 8 + 8.
    @Test
    fun theReservedWidthIsTheTrackPlusItsMargins() {
        assertEquals(CHROME_VOLUME_SLIDER_WIDTH.dp, expandedWidthFor(1280) + 8.dp + 8.dp)
    }

    @Test
    fun theDefaultIsAutoBecauseHoverExpandNeedsAPointer() {
        assertEquals(VolumeSliderMode.Auto, ChromeLayout().volumeSlider)
        assertEquals(
            VolumeSliderMode.Auto,
            VideoUiOptions(formFactor = tv.nomercy.player.core.device.FormFactor.Desktop).volumeSlider,
        )
    }
}
