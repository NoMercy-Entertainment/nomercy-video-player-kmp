// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// That the table is a table, rather than English filed under 79 names.
//
// Two generated assets in this package shipped English in every locale because a
// key existed and nothing read it, and both looked correct from the inside: the
// file was large, the locales were all present, and every lookup returned a word.
// The only check that would have caught either is the one below — that a locale
// which should differ does.
class TvKeyHandlerTranslationsTest {

    @Test
    fun aTranslatedLocaleDoesNotReadAsEnglish() {
        assertNotEquals(
            TvKeyHandlerTranslations.get(TvKeyHandlerTranslations.FALLBACK, KEY_CHAPTER),
            TvKeyHandlerTranslations.get("nl", KEY_CHAPTER),
        )
    }

    @Test
    fun theValuesAreTheWebPluginsOwnRatherThanAParaphrase() {
        assertEquals("Hoofdstuk", TvKeyHandlerTranslations.get("nl", KEY_CHAPTER))
        assertEquals("Beeldverhouding", TvKeyHandlerTranslations.get("nl", KEY_ASPECT_RATIO))
        assertEquals("Aspect ratio", TvKeyHandlerTranslations.get("en", KEY_ASPECT_RATIO))
    }

    @Test
    fun noLocaleInTheTableIsJustEnglishUnderAnotherName() {
        // Per locale rather than in total, because one translated locale among 79
        // is what a half-generated table looks like and it passes the check above.
        //
        // None, not few. Every one of the web's 79 files differs from English
        // somewhere, so a locale that matches it exactly did not come from the
        // web files — it came from a fallback that quietly filled the gap.
        val english: Map<String, String> = TvKeyHandlerTranslations.of("en")
        val identical: List<String> = TvKeyHandlerTranslations.available
            .filter { it != TvKeyHandlerTranslations.FALLBACK }
            .filter { TvKeyHandlerTranslations.of(it) == english }

        assertEquals(emptyList(), identical)
    }

    @Test
    fun aRegionalTagFindsItsBaseLanguage() {
        // nl-BE has no file of its own. It has to read Dutch rather than English.
        assertEquals(
            TvKeyHandlerTranslations.get("nl", KEY_CHAPTER),
            TvKeyHandlerTranslations.get("nl-BE", KEY_CHAPTER),
        )
    }

    @Test
    fun anUnknownLocaleReadsEnglishRatherThanNothing() {
        assertEquals("Chapter", TvKeyHandlerTranslations.get("qq", KEY_CHAPTER))
    }

    @Test
    fun everyLanguageTheWebPluginShipsIsHere() {
        // The count of i18n files under the web plugin, which is also the chrome
        // table's count because one generator writes both from folders that are
        // kept in step. A table short of a language the bar has is a viewer
        // reading a translated bar over an English info panel.
        assertEquals(WEB_LOCALE_COUNT, TvKeyHandlerTranslations.available.size)
    }

    private companion object {
        const val KEY_CHAPTER = "plugin.tv-key-handler.info.chapter"
        const val KEY_ASPECT_RATIO = "plugin.tv-key-handler.aspectRatio.cycled"

        const val WEB_LOCALE_COUNT = 79
    }
}
