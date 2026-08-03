// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The cast strings were the one plugin bundle with no native table at all, so
// every string a cast UI needed came back as its own key.
class CastTranslationsTest {

    @Test
    fun everyLocaleTheWebShipsIsHere() {
        // 79, the same count the chrome and the remote handler tables carry. A
        // table that quietly lost a locale reads as a working one until somebody
        // opens the player in it.
        assertEquals(79, CastTranslations.available.size)
        assertTrue("nl" in CastTranslations.available)
        assertTrue("pt-BR" in CastTranslations.available)
        assertTrue("zh-TW" in CastTranslations.available)
    }

    @Test
    fun theStringsAreTheWebsRatherThanARewordingOfThem() {
        assertEquals("Cast", CastTranslations.get("en", "plugin.cast-sender.action.connect"))
        assertEquals("Stop casting", CastTranslations.get("en", "plugin.cast-sender.action.disconnect"))
        assertEquals(
            "Casten is niet beschikbaar op dit apparaat.",
            CastTranslations.get("nl", "plugin.cast-sender.unavailable"),
        )
    }

    @Test
    fun aPlaceholderSurvivesTheTripIntact() {
        // {device} is substituted by the host. A generator that ate the braces
        // would leave a viewer reading "Casting to " with nothing after it.
        assertTrue(CastTranslations.get("nl", "plugin.cast-sender.connecting").contains("{device}"))
        assertTrue(CastTranslations.get("en", "plugin.cast-sender.state.playing").contains("{device}"))
    }

    @Test
    fun aRegionalTagFallsBackToItsLanguageAndAnUnknownOneToEnglish() {
        assertEquals(
            CastTranslations.get("nl", "plugin.cast-sender.action.connect"),
            CastTranslations.get("nl-BE", "plugin.cast-sender.action.connect"),
        )
        assertEquals("Cast", CastTranslations.get("qq", "plugin.cast-sender.action.connect"))
    }

    @Test
    fun anUnknownKeyComesBackAsItselfRatherThanAsBlank() {
        // A visible key is a bug report; a blank button is not.
        assertEquals("plugin.cast-sender.nope", CastTranslations.get("en", "plugin.cast-sender.nope"))
    }
}
