// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.ui.chrome.ChromeTranslations

public data class MenuStrings(
    val quality: String = "Quality",
    val audio: String = "Audio",
    val subtitles: String = "Subtitles",
    val subtitlesOff: String = "Off",
    val speed: String = "Speed",
    val playlist: String = "Playlist",

    // The aspect menu's labels, which are the web's words rather than its
    // tokens: a viewer reads "Crop", not "exactfit".
    val aspectRatio: String = "Aspect ratio",
    val aspectOriginal: String = "Original",
    val aspectStretch: String = "Stretch",
    val aspectCrop: String = "Crop",
    val aspectNative: String = "Native",

    // The subtitle settings list. One label per property plus the reset, in the
    // web's own words from its menu.subtitle.* keys.
    // The card's own header, which the port had no field for because it had no
    // header. `plugin.desktop-ui.menu.settings` carries it in all 79 locales.
    val settings: String = "Settings",

    // The header's two buttons, which were described with the PANE'S name — so a
    // screen reader announced "Subtitles" for a close button, and the subtitle
    // control on the bar and the close cross became the same node to a query.
    val back: String = "Back",
    val close: String = "Close",

    val subtitleSettings: String = "Subtitle settings",
    val subtitleFont: String = "Font",
    val subtitleTextSize: String = "Text size",
    val subtitleTextColor: String = "Text color",
    val subtitleTextOpacity: String = "Text opacity",
    val subtitleEdgeStyle: String = "Edge style",
    val subtitleBackgroundColor: String = "Background color",
    val subtitleBackgroundOpacity: String = "Background opacity",
    val subtitleAreaColor: String = "Area color",
    val subtitleAreaOpacity: String = "Area opacity",
    // The auto-skip row and its two options, from his own
    // settings_auto_skip_chapters, player_on and player_off.
    val autoSkipChapters: String = "Auto-skip intros/outros",
    val on: String = "On",
    val off: String = "Off",

    val reset: String = "Reset",
    val automatic: String = "Auto",
    val normalSpeed: String = "Normal",

    // The playlist pane's four, and the last three carry a `{number}` the caller
    // fills. Held as the template rather than as a formatter so the placeholder
    // travels with the translation: a language that puts the number first says so
    // in its own string, and a Kotlin format built here would put it second in
    // all seventy-nine.
    //
    // `episodes` rather than `playlist` is the pane's header over a run of
    // television, which is what `renderPlaylistPane` decides from the queue.
    val episodes: String = "Episodes",
    val season: String = "Season {number}",
    val seasonToken: String = "S{number}",
    val episodeToken: String = "E{number}",
)

/**
 * The menu's labels for a locale, read from the web's own table.
 *
 * There was no such function, so every consumer got the English defaults above
 * while ChromeTranslations sat in the same package carrying these exact keys in
 * seventy-nine languages. The same failure as the generated icons nobody drew:
 * the translated strings existed and nothing reached for them.
 *
 * Keys are the web's, and the wording with them. "Crop" rather than "Cover"
 * because that is the word a viewer of the browser player has already read.
 */
public fun menuStrings(locale: String): MenuStrings = playbackStrings(locale).copy(
    subtitleSettings = menuString(locale, "subtitleSettings"),
    subtitleFont = menuString(locale, "subtitle.font"),
    subtitleTextSize = menuString(locale, "subtitle.textSize"),
    subtitleTextColor = menuString(locale, "subtitle.textColor"),
    subtitleTextOpacity = menuString(locale, "subtitle.textOpacity"),
    subtitleEdgeStyle = menuString(locale, "subtitle.edgeStyle"),
    subtitleBackgroundColor = menuString(locale, "subtitle.backgroundColor"),
    subtitleBackgroundOpacity = menuString(locale, "subtitle.backgroundOpacity"),
    subtitleAreaColor = menuString(locale, "subtitle.areaColor"),
    subtitleAreaOpacity = menuString(locale, "subtitle.areaOpacity"),
    reset = menuString(locale, "reset"),

    // The auto-skip row's three words are NOT read from the table. The web has
    // no such row, so it has no keys for them, and `get` returns the key itself
    // when it finds nothing — which would put
    // "plugin.desktop-ui.menu.autoSkipChapters" on screen in all 79 locales,
    // English included. They stay on the data class, where a host that draws the
    // row overrides them from its own resources, which is where his three are.
)

// Split from `menuStrings` only because one function may not be forty lines
// long; the keys across both are the web's and the order is its menu's.
private fun playbackStrings(locale: String): MenuStrings = MenuStrings(
    quality = menuString(locale, "quality"),
    audio = menuString(locale, "audio"),
    subtitles = menuString(locale, "subtitles"),
    subtitlesOff = menuString(locale, "off"),
    speed = menuString(locale, "speed"),
    automatic = menuString(locale, "auto"),
    normalSpeed = menuString(locale, "normal"),
    playlist = menuString(locale, "playlist"),
    aspectRatio = menuString(locale, "aspectRatio"),
    aspectOriginal = menuString(locale, "original"),
    aspectStretch = menuString(locale, "stretch"),
    aspectCrop = menuString(locale, "crop"),
    aspectNative = menuString(locale, "native"),
    settings = menuString(locale, "settings"),
    back = menuString(locale, "back"),
    close = ChromeTranslations.get(locale, "plugin.desktop-ui.tooltip.close"),
    episodes = menuString(locale, "episodes"),
    season = menuString(locale, "season"),
    seasonToken = ChromeTranslations.get(locale, "plugin.desktop-ui.token.season"),
    episodeToken = ChromeTranslations.get(locale, "plugin.desktop-ui.token.episode"),
)

private fun menuString(locale: String, name: String): String =
    ChromeTranslations.get(locale, "$MENU_KEY_PREFIX$name")

/**
 * The pane's own header — "Episodes" over television, "Playlist" over anything else.
 *
 * `renderPlaylistPane`'s own test: an item whose `playlist_type` is `tv`, or one
 * carrying an episode number that is not a film. A movie collection is a playlist
 * and a run of episodes is not, and one header for both would be wrong for one of
 * them every time.
 */
internal fun playlistTitle(strings: MenuStrings, queue: List<TvChromeItem>): String {
    val television: Boolean = queue.any { item ->
        item.playlistType == TV_TYPE ||
            (item.episode != null && item.videoType != MOVIE_TYPE && item.playlistType != MOVIE_TYPE)
    }

    return if (television) strings.episodes else strings.playlist
}

// `t('plugin.desktop-ui.menu.season', { number })`.
internal fun seasonLabel(strings: MenuStrings, season: Int): String =
    strings.season.withNumber(season)

/**
 * The card's top-left label, from `buildPlaylistCard`.
 *
 * Three cases and each one is a real shape: a numbered episode of a numbered
 * season reads "S1: E4"; an item with an episode and no season at all reads "E4",
 * because a collection numbers its entries without seasoning them; and everything
 * else — a film, a season-0 special — reads its position in the queue. Season 0
 * deliberately falls to the last case: "S0: E4" is a bucket name, not a label.
 */
internal fun episodeToken(strings: MenuStrings, item: TvChromeItem, index: Int): String {
    val episode: Int = item.episode ?: return "${index + 1}"
    val season: Int? = item.season

    return when {
        season != null && season >= FIRST_REAL_SEASON ->
            "${strings.seasonToken.withNumber(season)}: ${strings.episodeToken.withNumber(episode)}"

        season == null -> strings.episodeToken.withNumber(episode)
        else -> "${index + 1}"
    }
}

private fun String.withNumber(value: Int): String = replace(NUMBER_PLACEHOLDER, value.toString())

private const val MENU_KEY_PREFIX = "plugin.desktop-ui.menu."
private const val NUMBER_PLACEHOLDER = "{number}"

// Season 0 is the specials bucket — a real season number and not one a viewer
// navigates by, which is why `shouldShowSeasonSidebar` excludes it and why the
// card does not label by it either.
private const val FIRST_REAL_SEASON = 1

private const val TV_TYPE = "tv"
private const val MOVIE_TYPE = "movie"
