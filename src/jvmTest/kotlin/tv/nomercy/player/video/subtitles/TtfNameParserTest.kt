// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The name reader, against real fonts.
//
// A hand-built name table would prove the arithmetic and miss the thing that
// actually breaks: real fonts carry a dozen records in several languages, and
// which one wins is the whole job. Arial Bold has a Catalan Windows record
// reading "Arial Negreta" sitting next to the English one.
//
// The fonts come from the system rather than the repository. Shipping a copy of
// Arial to test a parser would be shipping a licensed typeface, and the parser
// does not care whose font it is.
class TtfNameParserTest {

    private fun systemFont(name: String): ByteArray? {
        val candidates: List<String> = listOf(
            "C:/Windows/Fonts/$name",
            "/usr/share/fonts/truetype/$name",
            "/System/Library/Fonts/Supplemental/$name",
        )
        return candidates.map(::File).firstOrNull { it.isFile }?.readBytes()
    }

    @Test
    fun aFontReportsItsFullNameRatherThanItsFamily() {
        // The claim libass depends on. A script asking for "Arial Bold" matches
        // the string a font reports, so registering this one as "Arial" leaves
        // every bold line falling back to a default typeface — with nothing
        // failing and no message anywhere.
        val bytes: ByteArray = systemFont(ARIAL_BOLD) ?: run {
            println("arialbd.ttf not installed — skipping the real-font name gate")
            return
        }

        assertEquals(ARIAL_BOLD_NAME, TtfNameParser.extractFontName(bytes, ARIAL_BOLD_FALLBACK))
    }

    @Test
    fun aFontAnswersToItsFullNameAndToItsFamily() {
        // The defect this exists for. No Game No Life's styles ask for "Fontin
        // Sans Rg", the file reports full name "FontinSans-Bold" and family
        // "Fontin Sans Rg", and registering only the winner registered the one
        // name the script never uses — libass fell back to a wider face and the
        // dialogue ran off the frame, which read as a wrapping bug for weeks.
        //
        // Arial Bold has the same shape: full "Arial Bold", family "Arial".
        val bytes: ByteArray = systemFont(ARIAL_BOLD) ?: run {
            println("arialbd.ttf not installed — skipping the real-font name gate")
            return
        }

        val names: List<String> = TtfNameParser.extractFontNames(bytes, ARIAL_BOLD_FALLBACK)

        assertEquals(ARIAL_BOLD_NAME, names.first(), "the full name is no longer preferred")
        assertTrue(names.contains(ARIAL_NAME), "the family was lost: $names")
    }

    @Test
    fun aTranslatedRecordDoesNotWin() {
        // Arial Bold carries a Catalan Windows record reading "Arial Negreta".
        // A parser matching on platform and encoding alone picks it, and an
        // English script then finds nothing while the font sits there loaded.
        val bytes: ByteArray = systemFont(ARIAL_BOLD) ?: return

        val name: String = TtfNameParser.extractFontName(bytes, ARIAL_BOLD_FALLBACK)

        assertTrue(!name.contains("Negreta"), "a translated name won: $name")
    }

    @Test
    fun aFontWhoseFullNameEqualsItsFamilyStillReadsCleanly() {
        val bytes: ByteArray = systemFont(ARIAL) ?: return

        assertEquals(ARIAL_NAME, TtfNameParser.extractFontName(bytes, ARIAL_FALLBACK))
    }

    @Test
    fun bytesThatAreNotAFontFallBackRatherThanThrow() {
        // Fonts arrive attached to media files, and an attachment can be
        // anything. Throwing here would take down subtitle rendering for a
        // whole title because one attachment was a text file.
        assertEquals(FALLBACK, TtfNameParser.extractFontName(ByteArray(0), FALLBACK))
        assertEquals(FALLBACK, TtfNameParser.extractFontName(ByteArray(NOT_A_FONT_BYTES) { 0x41 }, FALLBACK))
    }

    @Test
    fun aTruncatedFontFallsBackRatherThanReadingPastTheEnd() {
        // A half-downloaded attachment is a real case, and reading past the end
        // of the array is a crash rather than a wrong name.
        val bytes: ByteArray = systemFont(ARIAL) ?: return
        val half: ByteArray = bytes.copyOfRange(0, bytes.size / 2)

        val name: String = TtfNameParser.extractFontName(half, ARIAL_FALLBACK)

        assertTrue(name == ARIAL_NAME || name == ARIAL_FALLBACK, "unexpected name from a truncated font: $name")
    }

    @Test
    fun theFallbackNameIsTheFilenameWithoutItsExtension() {
        assertEquals("Roboto-Medium", TtfNameParser.fallbackNameFor("Roboto-Medium.ttf"))
        assertEquals("SPLAT", TtfNameParser.fallbackNameFor("SPLAT.TTF"))
        assertEquals("no-extension", TtfNameParser.fallbackNameFor("no-extension"))
    }
}

private const val ARIAL_BOLD = "arialbd.ttf"
private const val ARIAL_BOLD_NAME = "Arial Bold"
private const val ARIAL_NAME = "Arial"
private const val ARIAL = "arial.ttf"
private const val ARIAL_BOLD_FALLBACK = "arialbd"
private const val ARIAL_FALLBACK = "arial"
private const val FALLBACK = "fallback"
private const val NOT_A_FONT_BYTES = 200
