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

// The web's own bands, asserted at their edges.
//
// The widths are the deliverable as much as the order is: a viewer who knows
// the settings button survives 480 and not 320 should find the same on a phone.
// Asserting at 320 and 321 rather than at 300 and 400 is what catches an
// off-by-one in the comparison, which is the way this goes wrong.
class ChromeResponsiveTest {

    @Test
    fun theBandsAreTheWebs() {
        assertEquals("xs", breakpointFor(320).name)
        assertEquals("sm", breakpointFor(321).name)
        assertEquals("sm", breakpointFor(480).name)
        assertEquals("md", breakpointFor(481).name)
        assertEquals("md", breakpointFor(720).name)
        assertEquals("lg", breakpointFor(721).name)
        assertEquals("lg", breakpointFor(1024).name)
        assertEquals("xl", breakpointFor(1025).name)
        assertEquals("xl", breakpointFor(3840).name)
    }

    // xs keeps rank 0 and 1: play and mute, and nothing else.
    @Test
    fun theNarrowestWindowKeepsPlayAndMuteOnly() {
        assertEquals(
            listOf(ChromeControl.PLAY, ChromeControl.MUTE),
            visibleControls(widthDp = 320),
        )
    }

    @Test
    fun smAddsVolumeFullscreenAndSettings() {
        val visible: List<ChromeControl> = visibleControls(widthDp = 480)

        assertEquals(
            listOf(
                ChromeControl.PLAY,
                ChromeControl.MUTE,
                ChromeControl.VOLUME,
                ChromeControl.FULLSCREEN,
                ChromeControl.SETTINGS,
            ),
            visible,
        )
    }

    @Test
    fun mdAddsTheQueueAndChapterJumps() {
        val visible: List<ChromeControl> = visibleControls(widthDp = 720)

        assertTrue(ChromeControl.NEXT in visible)
        assertTrue(ChromeControl.PREVIOUS in visible)
        assertTrue(ChromeControl.CHAPTER_PREV in visible)
        assertTrue(ChromeControl.CHAPTER_NEXT in visible)
        // Rank 9 and up are still out at md.
        assertFalse(ChromeControl.SEEK_BACK in visible)
        assertFalse(ChromeControl.THEATER in visible)
    }

    @Test
    fun lgAddsTheatrePipAndSpeed() {
        val visible: List<ChromeControl> = visibleControls(widthDp = 1024)

        assertTrue(ChromeControl.THEATER in visible)
        assertTrue(ChromeControl.PIP in visible)
        assertTrue(ChromeControl.SPEED in visible)
        assertFalse(ChromeControl.PLAYLIST in visible)
    }

    @Test
    fun theWidestWindowKeepsEverything() {
        assertEquals(ChromeControl.entries.size, visibleControls(widthDp = 1920).size)
    }

    // Not a width decision: a tall thin window has room across the bar and no
    // room for a thumb to hit one of nineteen targets in it.
    @Test
    fun portraitHidesItsSetAtAnyWidth() {
        val wide: List<ChromeControl> = visibleControls(widthDp = 1920, portrait = true)

        for (control in CHROME_PORTRAIT_HIDDEN) {
            assertFalse(control in wide, "$control survived portrait at 1920")
        }
        // And the rest of a wide window is still there.
        assertTrue(ChromeControl.THEATER in wide)
        assertTrue(ChromeControl.SPEED in wide)
    }

    // The two rules compose rather than one overriding the other.
    @Test
    fun portraitAndWidthBothApply() {
        val narrowPortrait: List<ChromeControl> = visibleControls(widthDp = 320, portrait = true)

        assertEquals(listOf(ChromeControl.PLAY, ChromeControl.MUTE), narrowPortrait)
    }

    // The list decides what collapses first, not what exists. A control nobody
    // ranked should not vanish because of that.
    @Test
    fun everyControlIsRanked() {
        val unranked: List<ChromeControl> = ChromeControl.entries.filter { it !in CHROME_PRIORITY }

        assertTrue(unranked.isEmpty(), "unranked: $unranked")
    }

    @Test
    fun thePriorityOrderIsTheWebs() {
        assertEquals(ChromeControl.PLAY, CHROME_PRIORITY.first())
        assertEquals(ChromeControl.PLAYLIST, CHROME_PRIORITY.last())
        assertEquals(19, CHROME_PRIORITY.size)
    }
}
