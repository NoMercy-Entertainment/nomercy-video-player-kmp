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
import kotlin.test.assertTrue

// What the bar actually DRAWS, down the path that decides it.
//
// Every other check here reads a list in a source file. That is why the wrong
// bar survived nine green gates: the eighteen controls really are written in
// TransportBar.kt in the web's order, and at runtime `visibleControls` filtered
// eleven of them out because the ChromeButtons defaults had them off. Source
// order and rendered order are different questions and only one of them is what
// somebody looks at.
//
// So this drives the same function the composable calls, with the preset the
// drop-in uses, at a width where nothing is dropped for room.
class FullPlayerBarTest {

    private val full: ChromeButtons = ChromeButtons.forKind(VideoUiKind.Full)

    // Everything the content supports. The gates that hide a control for missing
    // content are not what is being measured here.
    private fun drawnAt(widthDp: Int, buttons: ChromeButtons): List<ChromeControl> =
        visibleControls(
            widthDp = widthDp,
            contentHidden = { false },
            enabled = { buttons.allows(it) },
        )

    // The four menus, which is what he meant by "not my player": the bar he was
    // shown had a settings gear and nothing behind the other three.
    @Test
    fun aWidePlayerDrawsTheMenus() {
        val drawn: List<ChromeControl> = drawnAt(1920, full)

        assertTrue(ChromeControl.QUALITY in drawn, "no quality: $drawn")
        assertTrue(ChromeControl.SUBTITLES in drawn, "no subtitles: $drawn")
        assertTrue(ChromeControl.AUDIO in drawn, "no audio: $drawn")
        assertTrue(ChromeControl.PLAYLIST in drawn, "no playlist: $drawn")
    }

    @Test
    fun andTheChapterJumpsAndPictureInPicture() {
        val drawn: List<ChromeControl> = drawnAt(1920, full)

        assertTrue(ChromeControl.CHAPTER_PREV in drawn, "no chapter back: $drawn")
        assertTrue(ChromeControl.CHAPTER_NEXT in drawn, "no chapter forward: $drawn")
        assertTrue(ChromeControl.PIP in drawn, "no picture-in-picture: $drawn")
    }

    // The number that made the difference visible. Seven is what he was shown
    // and rejected; anything near it means the preset stopped reaching the bar.
    @Test
    fun theBarIsNotTheSevenControlOneHeRejected() {
        val mine: Int = drawnAt(1920, full).size
        val bare: Int = drawnAt(1920, ChromeButtons()).size

        assertTrue(mine >= 13, "the full bar drew only $mine controls")
        assertTrue(mine > bare, "the full bar ($mine) is no wider than the bare defaults ($bare)")
    }

    // And the bare defaults still are what they were, so the two cannot converge
    // by somebody "fixing" the constructor instead of the preset.
    @Test
    fun theBareDefaultsAreStillTheNarrowBar() {
        val bare: List<ChromeControl> = drawnAt(1920, ChromeButtons())

        assertEquals(false, ChromeControl.QUALITY in bare)
        assertEquals(false, ChromeControl.SUBTITLES in bare)
    }
}
