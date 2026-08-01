// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// That the season label is a table, rather than English filed under 79 names.
//
// Same shape as the remote handler's table test and for the same reason: a
// generated asset that shipped English everywhere looked correct from the
// inside, because the file was large and every lookup returned a word.
class MediaSessionTranslationsTest {

    @Test
    fun aTranslatedLocaleDoesNotReadAsEnglish() {
        assertNotEquals(
            MediaSessionTranslations.get(MediaSessionTranslations.FALLBACK, KEY_SEASON),
            MediaSessionTranslations.get("nl", KEY_SEASON),
        )
    }

    @Test
    fun theEnglishStringIsTheWebPluginsOwn() {
        assertEquals("Season {season}", MediaSessionTranslations.get("en", KEY_SEASON))
        assertEquals("Seizoen {season}", MediaSessionTranslations.get("nl", KEY_SEASON))
    }

    @Test
    fun everyLocaleCarriesThePlaceholderTheLabelSubstitutes() {
        // A locale whose string lost the token renders "Season" with no number,
        // which reads as a working label right up to the point somebody looks.
        val without: List<String> = MediaSessionTranslations.available
            .filterNot { MediaSessionTranslations.get(it, KEY_SEASON).contains("{season}") }

        assertEquals(emptyList(), without)
    }

    @Test
    fun aRegionalTagFindsItsBaseLanguage() {
        assertEquals(
            MediaSessionTranslations.get("nl", KEY_SEASON),
            MediaSessionTranslations.get("nl-BE", KEY_SEASON),
        )
    }

    @Test
    fun anUnknownLocaleReadsEnglishRatherThanNothing() {
        assertEquals("Season {season}", MediaSessionTranslations.get("qq", KEY_SEASON))
    }

    @Test
    fun everyLanguageTheWebPluginShipsIsHere() {
        assertEquals(WEB_LOCALE_COUNT, MediaSessionTranslations.available.size)
        assertTrue(MediaSessionTranslations.available.contains("pt-BR"), "a regional file was dropped")
    }

    private companion object {
        const val KEY_SEASON = "plugin.media-session.season"

        const val WEB_LOCALE_COUNT = 79
    }
}
