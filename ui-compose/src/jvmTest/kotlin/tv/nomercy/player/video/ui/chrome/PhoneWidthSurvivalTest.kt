// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import kotlin.test.Test

// What survives at each width, printed rather than asserted.
//
// Photographed on a real phone in landscape: the transport bar drew pause,
// volume and elapsed and nothing else, while the host had every button switched
// on. Two candidates -- the drop rule is wrong, or the width handed to it is
// far smaller than the pane -- and they need opposite fixes. This is the
// measurement that separates them: the rule is a pure function, so it can be
// asked directly what it would keep.
class PhoneWidthSurvivalTest {

    @Test
    fun whatEachWidthKeeps() {
        for (width in WIDTHS) {
            val kept: List<ChromeControl> = visibleControls(
                widthDp = width,
                noHover = true,
                contentHidden = { false },
                enabled = { true },
                priority = CHROME_PRIORITY,
                portraitHidden = CHROME_PORTRAIT_HIDDEN,
            )
            println("width=$width kept=${kept.size} -> ${kept.joinToString(",")}")
        }
    }

    private companion object {
        // A phone in landscape is 800-900dp of usable width; 360 is portrait,
        // and 240 is what a starved pane would hand over.
        val WIDTHS = listOf(240, 320, 360, 480, 640, 800, 900, 1280)
    }
}
