// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.video.ui.chrome.menus.MenuStrings
import tv.nomercy.player.video.ui.chrome.menus.menuStrings
import tv.nomercy.player.video.ui.tv.TvChromeStrings
import tv.nomercy.player.video.ui.tv.tvChromeStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// That the two builders produce something a viewer can read, and that a key the
// table does not carry never reaches the screen.
//
// These exist because both builders shipped with no caller at all. Every
// construction site wrote the English constructor, so nothing was ever wrong
// enough to fail — the player was simply English everywhere, in a build
// carrying 79 locales.
class ChromeLocaleTest {

    @Test
    fun aDutchViewerReadsDutch() {
        val dutch: TvChromeStrings = tvChromeStrings("nl")

        assertNotEquals(TvChromeStrings().next, dutch.next)
        assertEquals(ChromeTranslations.get("nl", "plugin.desktop-ui.tooltip.next"), dutch.next)
    }

    @Test
    fun andTheSettingsListIsDutchToo() {
        val dutch: MenuStrings = menuStrings("nl")

        assertNotEquals(MenuStrings().quality, dutch.quality)
        assertEquals(ChromeTranslations.get("nl", "plugin.desktop-ui.menu.quality"), dutch.quality)
    }

    // A region the table has no entry for falls back to the language, which is
    // what `of` strips for. Flemish is Dutch here.
    @Test
    fun aRegionalTagFindsItsLanguage() {
        assertEquals(menuStrings("nl").quality, menuStrings("nl-BE").quality)
    }

    @Test
    fun anUnknownLanguageReadsEnglish() {
        assertEquals(menuStrings("en").quality, menuStrings("zz").quality)
    }

    // The trap the auto-skip row walked into: `get` returns the KEY when it
    // finds nothing, so a label built from a key the web never had renders as
    // "plugin.desktop-ui.menu.autoSkipChapters" in every locale. Those three
    // words stay on the data class, and this fails if somebody routes them
    // through the table later.
    @Test
    fun theAutoSkipWordsNeverRenderAsAKey() {
        ChromeTranslations.available.forEach { locale ->
            val strings: MenuStrings = menuStrings(locale)

            listOf(strings.autoSkipChapters, strings.on, strings.off).forEach { word ->
                assertTrue(!word.startsWith("plugin."), "$locale rendered a raw key: $word")
            }
        }
    }
}
