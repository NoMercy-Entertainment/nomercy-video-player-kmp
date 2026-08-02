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

// That the entry point the SwiftUI bar calls gives the same answer as the one
// the Compose bar calls.
//
// The whole reason it exists is the ObjC bridge, and a bridge-shaped wrapper is
// exactly the kind of thing that quietly grows a rule of its own. Graded across
// the widths the bands meet at, in portrait and landscape, with and without a
// pointer — so a divergence anywhere in the accumulation shows up here rather
// than on a phone.
class ChromeResponsiveBridgeTest {

    @Test
    fun theBridgeAgreesWithTheRuleAtEveryBand() {
        CASES.forEach { (width: Int, orientation: Pair<Boolean, Boolean>) ->
            val (portrait: Boolean, noHover: Boolean) = orientation

            assertEquals(
                visibleControls(
                    widthDp = width,
                    portrait = portrait,
                    noHover = noHover,
                    contentHidden = { it in UNAVAILABLE },
                    enabled = { it in ENABLED },
                ),
                visibleControlsIn(
                    widthDp = width,
                    enabled = ENABLED,
                    unavailable = UNAVAILABLE,
                    portrait = portrait,
                    noHover = noHover,
                ),
                "width=$width portrait=$portrait noHover=$noHover",
            )
        }
    }

    // The measurement the iPhone failed on. A phone is 390-odd points across and
    // the bar drew all eighteen controls, so the row's own width came out far
    // past the screen and widened everything above it.
    @Test
    fun aPhoneWidthCannotFitEveryControl() {
        val visible: List<ChromeControl> = visibleControlsIn(
            widthDp = PHONE_WIDTH,
            enabled = CHROME_PRIORITY.toSet(),
            portrait = true,
            noHover = true,
        )

        val used: Int = visible.sumOf { controlFootprint(it, noHover = true) } + CHROME_RESERVED_WIDTH
        assertTrue(used <= PHONE_WIDTH, "the bar asked for $used points on a $PHONE_WIDTH point screen")
        assertTrue(visible.size < CHROME_PRIORITY.size, "a phone was handed every control: $visible")
    }
}

// Both edges of every band in CHROME_BREAKPOINTS, plus a phone and a desktop,
// each in every combination of orientation and pointer.
private val WIDTHS: List<Int> = listOf(0, 148, 320, 321, 390, 480, 481, 720, 721, 1024, 1025, 1920)

private val ORIENTATIONS: List<Pair<Boolean, Boolean>> =
    listOf(false to false, false to true, true to false, true to true)

private val CASES: List<Pair<Int, Pair<Boolean, Boolean>>> =
    WIDTHS.flatMap { width: Int -> ORIENTATIONS.map { width to it } }

// iPhone 17 Pro in portrait, which is the device the testbed capture came from.
private const val PHONE_WIDTH = 393

private val UNAVAILABLE: Set<ChromeControl> = setOf(ChromeControl.QUALITY, ChromeControl.PLAYLIST)
private val ENABLED: Set<ChromeControl> = CHROME_PRIORITY.toSet() - ChromeControl.AUDIO
