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

// The table is generated, so what is worth asserting is not its contents but
// the four ways a lookup can quietly do the wrong thing.
class ChromeTranslationsTest {

    @Test
    fun everyLocaleTheWebShipsIsHere() {
        // Eighty files on the web, one of which is the index. A drop below this
        // means the generator read the folder and found less than it should.
        assertTrue(
            ChromeTranslations.available.size >= EXPECTED_LOCALES,
            "only ${ChromeTranslations.available.size} locales",
        )
        assertTrue("nl" in ChromeTranslations.available)
        assertTrue("de" in ChromeTranslations.available)
        assertTrue("ja" in ChromeTranslations.available)
    }

    // The value the browser renders, not a rewording of it. This is the one
    // assertion that would catch somebody "improving" a string on the way in.
    @Test
    fun theStringIsTheWebsString() {
        assertEquals("Play / Pause", ChromeTranslations.get("en", "plugin.desktop-ui.tooltip.play"))
        assertEquals("Seek back 10 s", ChromeTranslations.get("en", "plugin.desktop-ui.tooltip.seekBack"))
    }

    @Test
    fun aTranslatedLocaleIsNotEnglish() {
        val dutch: String = ChromeTranslations.get("nl", "plugin.desktop-ui.tooltip.quality")
        val english: String = ChromeTranslations.get("en", "plugin.desktop-ui.tooltip.quality")

        assertTrue(dutch.isNotBlank())
        assertTrue(dutch != english, "nl returned the English string, so the table is not translated")
    }

    // A viewer on nl-BE has no nl-BE file and should read Dutch rather than
    // English. Region-stripping is what makes that happen.
    @Test
    fun aRegionalTagFallsBackToItsLanguage() {
        assertEquals(
            ChromeTranslations.get("nl", "plugin.desktop-ui.tooltip.quality"),
            ChromeTranslations.get("nl-BE", "plugin.desktop-ui.tooltip.quality"),
        )
    }

    @Test
    fun anUnknownLocaleReadsEnglish() {
        assertEquals(
            ChromeTranslations.get("en", "plugin.desktop-ui.tooltip.play"),
            ChromeTranslations.get("zz", "plugin.desktop-ui.tooltip.play"),
        )
    }

    // The failure mode worth naming: a missing key rendering as itself, so a
    // viewer reads 'plugin.desktop-ui.tooltip.play' in the middle of a player.
    @Test
    fun aKeyMissingFromALocaleReadsEnglishRatherThanTheKey() {
        val value: String = ChromeTranslations.get("nl", "plugin.desktop-ui.tooltip.play")

        assertTrue(value.isNotBlank())
        assertTrue(!value.startsWith("plugin."), "the key leaked to the screen: $value")
    }

    private companion object {
        const val EXPECTED_LOCALES = 75
    }
}
