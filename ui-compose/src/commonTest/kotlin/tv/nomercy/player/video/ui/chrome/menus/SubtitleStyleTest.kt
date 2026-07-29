// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The subtitle settings, against the web's own lists and defaults.
//
// This is a preference a viewer sets once and expects to find again — on the
// same account, in a different client. So the values have to be the web's values
// and not near them: a native player storing "large" where the browser stores
// 150 gives somebody two pictures of one preference and no way to reconcile
// them.
class SubtitleStyleTest {

    @Test
    fun theDefaultsAreTheWebsDefaults() {
        val style = SubtitleStyle()

        assertEquals(100, style.fontSize)
        assertEquals("ReithSans, sans-serif", style.fontFamily)
        assertEquals("white", style.textColor)
        assertEquals(100, style.textOpacity)
        assertEquals("black", style.backgroundColor)
        assertEquals(0, style.backgroundOpacity)
        assertEquals(SubtitleEdgeStyle.TextShadow, style.edgeStyle)
        assertEquals("black", style.areaColor)
        assertEquals(0, style.windowOpacity)
    }

    // Nine rows, in SETTING_ROWS order. The reset is the tenth row of the menu
    // and deliberately not a tenth entry here: it has no value to show and no
    // pane to open.
    @Test
    fun theRowsAreTheWebsRowsInOrder() {
        assertEquals(
            listOf(
                "fontFamily",
                "fontSize",
                "textColor",
                "textOpacity",
                "edgeStyle",
                "backgroundColor",
                "backgroundOpacity",
                "areaColor",
                "windowOpacity",
            ),
            SubtitleSetting.entries.map { it.property },
        )
    }

    @Test
    fun eachRowOffersTheWebsChoices() {
        assertEquals(5, SubtitleSetting.Font.choices().size)
        assertEquals(7, SubtitleSetting.TextSize.choices().size)
        assertEquals(8, SubtitleSetting.TextColor.choices().size)
        assertEquals(5, SubtitleSetting.TextOpacity.choices().size)
        assertEquals(6, SubtitleSetting.EdgeStyle.choices().size)

        // Stacks, not bare family names. The fallback after the comma is what a
        // device without the first face actually draws, and dropping it gives a
        // different letterform than the browser for the same stored value.
        assertTrue(SubtitleSetting.Font.choices().all { it.contains(", ") })
    }

    // The three colour rows share one list and the three opacity rows share
    // another, exactly as the web builds them from `colors` and `opacities`.
    @Test
    fun theColourRowsShareOneListAndTheOpacityRowsAnother() {
        assertEquals(SubtitleSetting.TextColor.choices(), SubtitleSetting.BackgroundColor.choices())
        assertEquals(SubtitleSetting.TextColor.choices(), SubtitleSetting.AreaColor.choices())
        assertEquals(SubtitleSetting.TextOpacity.choices(), SubtitleSetting.AreaOpacity.choices())
    }

    @Test
    fun aRowShowsWhatItCurrentlyReads() {
        val style = SubtitleStyle(fontSize = 150, textColor = "yellow", windowOpacity = 75)

        assertEquals("150%", SubtitleSetting.TextSize.valueOf(style))
        assertEquals("Yellow", SubtitleSetting.TextColor.valueOf(style))
        assertEquals("75%", SubtitleSetting.AreaOpacity.valueOf(style))
        assertEquals("ReithSans", SubtitleSetting.Font.valueOf(style))
    }

    @Test
    fun pickingAChoiceChangesThatFieldAndNoOther() {
        val style = SubtitleStyle()

        val changed = SubtitleSetting.TextSize.applied(style, "200%")

        assertEquals(200, changed.fontSize)
        assertEquals(style.copy(fontSize = 200), changed)
    }

    // A choice arrives as the text of the row that was pressed, sign included.
    @Test
    fun aPercentageIsReadBackOffTheRowsOwnLabel() {
        val style = SubtitleStyle()

        SubtitleSetting.TextOpacity.choices().forEach { choice ->
            val applied = SubtitleSetting.TextOpacity.applied(style, choice)
            assertEquals(choice, SubtitleSetting.TextOpacity.valueOf(applied))
        }
    }

    // Anything that failed to parse leaves the picture alone. Falling back to
    // zero would blank the subtitles for a value nobody could have chosen.
    @Test
    fun anUnreadableChoiceKeepsWhatWasAlreadySet() {
        val style = SubtitleStyle(fontSize = 150)

        assertEquals(150, SubtitleSetting.TextSize.applied(style, "enormous").fontSize)
    }

    @Test
    fun everyEdgeStyleRoundTripsThroughItsToken() {
        SubtitleEdgeStyle.entries.forEach { edge ->
            assertEquals(edge, SubtitleEdgeStyle.fromToken(edge.token))
        }
    }
}
