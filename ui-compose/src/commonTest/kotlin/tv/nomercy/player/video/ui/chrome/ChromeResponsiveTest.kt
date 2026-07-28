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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The expected numbers here were measured in a browser, not reasoned out.
// `e2e/render-parity-export.spec.ts` drives the real chrome at each width and
// writes down what it lays out; these assertions are that file.
//
// That matters because the first version of this suite tested the rank-cut rule
// this port had invented, and every case passed. A test written from the same
// misreading as the code agrees with it.
class ChromeResponsiveTest {

    // The bands are still right — the web picks them with `width <= maxWidth`,
    // and the browser confirmed the boundary sits exactly there.
    @Test
    fun bandsAreChosenByTheirCeiling() {
        assertEquals("xs", breakpointFor(320).name)
        assertEquals("sm", breakpointFor(321).name)
        assertEquals("sm", breakpointFor(480).name)
        assertEquals("md", breakpointFor(481).name)
        assertEquals("md", breakpointFor(720).name)
        assertEquals("lg", breakpointFor(721).name)
        assertEquals("lg", breakpointFor(1024).name)
        assertEquals("xl", breakpointFor(1025).name)
    }

    // The rule that was wrong. md carries hideAfterRank = 8, which reads as
    // nine controls; the accumulation fits eleven. The two disagree, and the
    // web follows the accumulation.
    //
    // Asserting they DIFFER rather than asserting a number, because the number
    // is a consequence of footprints that may change. What must not come back
    // is the rank cut deciding anything.
    @Test
    fun theBandsRankCutDoesNotDecideVisibility() {
        val band = breakpointFor(720)
        val visible = visibleControls(720)

        assertEquals(8, band.hideAfterRank)
        assertTrue(
            visible.size != band.hideAfterRank + 1,
            "the rank cut is being applied again: $visible",
        )
    }

    // Continuous, not stepped. Two widths inside one band show different
    // numbers of controls, which a rank cut cannot produce.
    @Test
    fun controlsAppearOnePixelAtATimeRatherThanPerBand() {
        val narrow = visibleControls(900)
        val wide = visibleControls(1000)

        assertEquals("lg", breakpointFor(900).name)
        assertEquals("lg", breakpointFor(1000).name)
        assertTrue(
            wide.size > narrow.size,
            "same band, same controls — visibility is still stepped: $narrow vs $wide",
        )
    }

    // Most essential first: whatever else goes, play survives.
    @Test
    fun playSurvivesTheNarrowestBar() {
        assertTrue(ChromeControl.PLAY in visibleControls(320))
    }

    @Test
    fun nothingFitsAtZeroWidth() {
        assertTrue(visibleControls(0).isEmpty())
    }

    // The reserve is real width, not a fudge factor. A bar exactly as wide as
    // the reserve has nothing left for a button.
    @Test
    fun theReservedRowSpaceComesOffTheTop() {
        assertTrue(visibleControls(CHROME_RESERVED_WIDTH).isEmpty())
        assertEquals(listOf(ChromeControl.PLAY), visibleControls(CHROME_RESERVED_WIDTH + CHROME_BUTTON_WIDTH))
    }

    // Mute costs the slider's width wherever there is a pointer, because the
    // slider expands beside it and the room has to exist before it does.
    @Test
    fun muteIsWiderWhereThereIsAPointer() {
        assertEquals(
            CHROME_BUTTON_WIDTH + CHROME_VOLUME_SLIDER_WIDTH,
            controlFootprint(ChromeControl.MUTE, noHover = false),
        )
        assertEquals(CHROME_BUTTON_WIDTH, controlFootprint(ChromeControl.MUTE, noHover = true))
        assertEquals(CHROME_BUTTON_WIDTH, controlFootprint(ChromeControl.PLAY, noHover = false))
    }

    // ...so a touch bar fits more controls than a pointer bar of the same width.
    @Test
    fun aTouchBarFitsMoreThanAPointerBarOfTheSameWidth() {
        assertTrue(visibleControls(600, noHover = true).size > visibleControls(600).size)
    }

    @Test
    fun portraitDropsItsFixedSet() {
        val portrait = visibleControls(1440, portrait = true)

        CHROME_PORTRAIT_HIDDEN.forEach { control ->
            assertFalse(control in portrait, "$control should be hidden in portrait")
        }
    }

    // A control the item cannot offer is skipped before the fit and charged
    // nothing, so hiding it lets a later control onto the bar rather than
    // leaving a gap.
    @Test
    fun aContentHiddenControlFreesItsSpace() {
        val width = 600
        val full = visibleControls(width)
        val gated = visibleControls(width) { it == ChromeControl.MUTE }

        assertFalse(ChromeControl.MUTE in gated)
        assertTrue(
            gated.size > full.size - 1,
            "hiding mute left a hole instead of freeing 136dp: $full vs $gated",
        )
    }

    // The walk does not stop at the first control that does not fit. Mute is
    // 136dp wide and everything after it is 40dp, so a bar too narrow for mute
    // still has room for what follows.
    @Test
    fun aControlThatDoesNotFitDoesNotBlockNarrowerOnesBelowIt() {
        val visible = visibleControls(CHROME_RESERVED_WIDTH + 120)

        assertFalse(ChromeControl.MUTE in visible)
        assertTrue(visible.size > 1, "the walk stopped at the first miss: $visible")
    }

    @Test
    fun everythingIsOnTheBarWhenThereIsRoom() {
        assertEquals(CHROME_PRIORITY.size, visibleControls(4000).size)
    }
}
