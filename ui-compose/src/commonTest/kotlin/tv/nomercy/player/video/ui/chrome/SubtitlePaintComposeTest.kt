// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

// The two conversions between the style a viewer chose and the numbers Compose
// draws with.
//
// Both are places where a subtitle can be technically present and invisible: a
// colour whose alpha byte landed in the wrong end of the word draws transparent,
// and a size that ignored the picture's width draws fourteen pixels of text
// across a television.
class SubtitlePaintComposeTest {

    @Test
    fun aColourKeepsItsChannelsAndGetsItsOpacity() {
        assertEquals(Color(red = 0, green = 255, blue = 255, alpha = 255), subtitleColor("cyan", 100))
        assertEquals(Color(red = 255, green = 255, blue = 255, alpha = 128), subtitleColor("white", 50))
    }

    @Test
    fun theAlphaMovesFromTheEndOfTheHexToTheFrontOfTheWord() {
        // CSS writes `#RRGGBBAA` and Compose holds ARGB. Reading the eight
        // digits straight through compiles, and paints a fully transparent
        // subtitle in a colour nobody chose.
        assertEquals(Color(red = 0, green = 0, blue = 0, alpha = 0), subtitleColor("black", 0))
        assertEquals(Color(red = 0, green = 0, blue = 0, alpha = 255), subtitleColor("black", 100))
    }

    @Test
    fun anUnresolvableColourIsTransparentRatherThanBlack() {
        assertEquals(Color.Transparent, subtitleColor("papayawhip", 100))
    }

    @Test
    fun theSizeIsAShareOfThePictureRatherThanAFixedNumber() {
        // `2.5cqi` — a subtitle is the same share of a phone and of a
        // television, which is what a fixed point size cannot be.
        assertEquals(48f, cueFontSize(1920.dp, 100).value)
        assertEquals(24f, cueFontSize(960.dp, 100).value)
    }

    @Test
    fun andTheViewersOwnPercentageScalesIt() {
        assertEquals(24f, cueFontSize(960.dp, 100).value)
        assertEquals(36f, cueFontSize(960.dp, 150).value)
    }

    @Test
    fun theClampHoldsBothEnds() {
        // WCAG 1.4.4 at the bottom and legibility at the top. Without it, 400%
        // on a wide window draws a caption taller than the picture.
        assertEquals(14f, cueFontSize(200.dp, 100).value)
        assertEquals(56f, cueFontSize(1920.dp, 400).value)
    }
}
