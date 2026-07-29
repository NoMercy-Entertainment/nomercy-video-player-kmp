// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import tv.nomercy.player.video.ui.chrome.menus.MenuStrings
import tv.nomercy.player.video.ui.chrome.menus.menuStrings
import tv.nomercy.player.video.ui.tv.TvChromeStrings
import tv.nomercy.player.video.ui.tv.tvChromeStrings

/**
 * The device's language, in the form the generated table is keyed by.
 *
 * `ChromeTranslations.of` takes the region off itself, so a tag is handed over
 * whole: `nl-BE` finds `nl`, and a table that one day carries `pt-BR` separately
 * from `pt` gets the tag it needs to tell them apart.
 */
@Composable
public fun rememberChromeLocale(): String {
    val locale: Locale = Locale.current

    return remember(locale) { locale.toLanguageTag() }
}

/**
 * The bar's labels for the viewer's own language.
 *
 * `tvChromeStrings` and `menuStrings` existed for a while with no caller at all,
 * which is a whole different failure from being untranslated: 79 locales and 113
 * keys sat in the build, every construction site wrote `TvChromeStrings()`, and
 * every player shipped English. Nothing was red — the defaults on the data class
 * are English by design so a hand-built one reads, and they were what everybody
 * got.
 *
 * So the defaults on the composables are these, not the constructors. A consumer
 * who wants a fixed language still passes one; a consumer who passes nothing now
 * gets the viewer's, which is what they would have assumed they were getting.
 */
@Composable
public fun rememberChromeStrings(locale: String = rememberChromeLocale()): TvChromeStrings =
    remember(locale) { tvChromeStrings(locale) }

/** The settings list's labels, for the same locale and the same reason. */
@Composable
public fun rememberMenuStrings(locale: String = rememberChromeLocale()): MenuStrings =
    remember(locale) { menuStrings(locale) }
