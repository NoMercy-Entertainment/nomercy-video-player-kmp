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

// The plugin's options, and that they actually change the bar.
//
// VideoUiOptions had four fields against DesktopUiOptions' fourteen, and the
// missing ones were not obscure: his own site passes buttonPriority,
// portraitHidden and buttonOrder. A consumer moving that line across got a chrome
// that ignored most of it, and nothing was red — an option a data class does not
// declare is a compile error at the CALL site, so the code that never wrote it
// compiled fine and quietly got the default.
//
// An option that exists and changes nothing is worse than an absent one, so these
// drive `visibleControls` rather than reading the field back.
class VideoUiOptionsTest {

    private val everything: (ChromeControl) -> Boolean = { true }

    @Test
    fun raisingAControlKeepsItOnANarrowBar() {
        // Wide enough for a few controls, not for all of them.
        val width = 420

        val byDefault = visibleControls(width, contentHidden = { false }, enabled = everything)
        assertTrue(ChromeControl.PLAYLIST !in byDefault, "playlist already fits at $width")

        // His site's move: raise what a thumb reaches for above the menus.
        val raised = listOf(ChromeControl.PLAY, ChromeControl.PLAYLIST) +
            CHROME_PRIORITY.filterNot { it == ChromeControl.PLAY || it == ChromeControl.PLAYLIST }

        val reordered = visibleControls(
            width,
            contentHidden = { false },
            enabled = everything,
            priority = raised,
        )

        assertTrue(ChromeControl.PLAYLIST in reordered, "the priority override did nothing: $reordered")
    }

    // The set is replaceable, not just present. His site hides a different six.
    @Test
    fun portraitHiddenIsTheConsumersSetNotTheLibrarys() {
        val hidden = visibleControls(
            1600,
            portrait = true,
            contentHidden = { false },
            enabled = everything,
            portraitHidden = setOf(ChromeControl.SETTINGS),
        )

        assertTrue(ChromeControl.SETTINGS !in hidden, "the replacement set was ignored")

        // And a control the LIBRARY hides in portrait comes back, which is what
        // proves the default was replaced rather than merged.
        val libraryHides: ChromeControl = CHROME_PORTRAIT_HIDDEN.first { it != ChromeControl.SETTINGS }
        assertTrue(libraryHides in hidden, "$libraryHides is still hidden: the sets were merged")
    }

    @Test
    fun theDefaultsAreStillTheWebs() {
        val options = VideoUiOptions(formFactor = tv.nomercy.player.core.device.FormFactor.Desktop)

        assertEquals(CHROME_PRIORITY, options.buttonPriority)
        assertEquals(CHROME_PORTRAIT_HIDDEN, options.portraitHidden)
        assertEquals(DEFAULT_INACTIVITY_MS, options.inactivityMs)
        assertEquals(false, options.hideTitle)
        assertEquals(false, options.disableClickToPause)
    }
}
