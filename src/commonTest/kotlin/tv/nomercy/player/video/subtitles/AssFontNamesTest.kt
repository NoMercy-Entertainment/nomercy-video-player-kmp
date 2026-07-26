// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A real .ass file's shape, not a minimal one. The sections, the Format line
// before the Style lines, an override tag mid-dialogue, and a Comment — all of
// which appear in files that come off a disc.
private val SUBTITLE = """
    [Script Info]
    Title: Something
    ScriptType: v4.00+

    [V4+ Styles]
    Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour
    Style: Default,Roboto Condensed,48,&H00FFFFFF,&H000000FF
    Style: Sign,Bebas Neue,64,&H00FFFFFF,&H000000FF
    Style: Fallback,Arial,48,&H00FFFFFF,&H000000FF

    [Events]
    Format: Layer, Start, End, Style, Text
    Dialogue: 0,0:00:01.00,0:00:03.00,Default,,Hello there
    Dialogue: 0,0:00:04.00,0:00:06.00,Default,,{\fnNoto Sans JP\b1}Inline override
    Comment: 0,0:00:07.00,0:00:08.00,Sign,,{\fnComment Only}Not shown
""".trimIndent()

class AssFontNamesTest {

    @Test
    fun everyFontAStyleNamesIsFound() {
        val fonts = AssFontNames.parse(SUBTITLE)

        assertTrue(fonts.contains("Roboto Condensed"))
        assertTrue(fonts.contains("Bebas Neue"))
    }

    @Test
    fun anInlineOverrideNamesAFontTheStylesNeverMention() {
        // A file whose styles all say Arial can still need three other fonts,
        // one per line, and reading only the styles would fetch none of them.
        assertTrue(AssFontNames.parse(SUBTITLE).contains("Noto Sans JP"))
    }

    @Test
    fun aCommentLineCountsBecauseAPlayerMayStillRenderIt() {
        assertTrue(AssFontNames.parse(SUBTITLE).contains("Comment Only"))
    }

    @Test
    fun arialIsSkippedBecauseEveryRendererAlreadyHasIt() {
        // Fetching it costs a request that can only return what libass was going
        // to fall back to anyway.
        assertTrue(!AssFontNames.parse(SUBTITLE).contains("Arial"))
    }

    @Test
    fun aFontNamedTwiceIsOneFont() {
        val twice = """
            [V4+ Styles]
            Style: A,Roboto,48
            Style: B,Roboto,64
        """.trimIndent()

        assertEquals(listOf("Roboto"), AssFontNames.parse(twice))
    }

    @Test
    fun theOrderIsTheOrderTheyWereFound() {
        // Stable output matters: the fetch order decides which font is available
        // first, and a set with arbitrary iteration makes that irreproducible.
        assertEquals(
            listOf("Roboto Condensed", "Bebas Neue", "Noto Sans JP", "Comment Only"),
            AssFontNames.parse(SUBTITLE),
        )
    }

    @Test
    fun aStyleLineOutsideAStyleSectionIsNotAStyle() {
        val misplaced = """
            [Script Info]
            Style: Ignored,Should Not Appear,48

            [V4+ Styles]
            Style: Real,Roboto,48
        """.trimIndent()

        assertEquals(listOf("Roboto"), AssFontNames.parse(misplaced))
    }

    @Test
    fun anOverrideTagStopsAtTheNextTagOrTheClosingBrace() {
        val tricky = """
            [Events]
            Dialogue: 0,0,0,D,,{\fnFirst Font\i1}text{\fnSecond Font}more
        """.trimIndent()

        assertEquals(listOf("First Font", "Second Font"), AssFontNames.parse(tricky))
    }

    @Test
    fun aFileWithNothingToFetchAsksForNothing() {
        assertEquals(emptyList(), AssFontNames.parse(""))
        assertEquals(emptyList(), AssFontNames.parse("[Script Info]\nTitle: Nothing"))
    }

    @Test
    fun aMalformedStyleLineIsSkippedRatherThanCrashing() {
        // Subtitles arrive from wherever the file came from, which is not a
        // place with a schema.
        assertEquals(emptyList(), AssFontNames.parse("[V4+ Styles]\nStyle: OnlyAName"))
    }
}
