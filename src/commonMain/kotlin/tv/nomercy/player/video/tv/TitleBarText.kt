// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

/**
 * The two lines across the top of the player.
 *
 * From `desktop-ui/helpers/topBar.ts updateTitleBar`. Four rules, and three of
 * them only appear on content most people never test with:
 *
 * 1. **The primary line is the show for a series and the title for anything
 *    else.** A film has no show, so its own title goes on top.
 * 2. **The secondary line is suppressed when the episode title equals the show
 *    name.** Servers do that for untitled episodes, and without the check the
 *    player prints the show name twice, one line under the other.
 * 3. **Season 0 is "Extras", not "Season 0".** It is the specials bucket, and
 *    a viewer reading "Season 0 Episode 3" has been shown an implementation
 *    detail.
 * 4. **A negative or missing season drops the season entirely** and shows only
 *    the episode label.
 *
 * The labels come from the translator so they arrive in the viewer's language;
 * this decides which of them to ask for and how to join them.
 */
public data class TitleBarText(
    val primary: String,
    val secondary: String,
) {
    /** The web hides the element rather than rendering an empty one. */
    public val showsSecondary: Boolean get() = secondary.isNotEmpty()
}

/**
 * [seasonLabel] and [episodeLabel] take a number and return the localised
 * label; [extrasLabel] takes none. They are the translator's
 * `plugin.desktop-ui.token.season`, `.episode` and `.extras`.
 */
public fun titleBarText(
    item: TvChromeItem?,
    seasonLabel: (Int) -> String,
    episodeLabel: (Int) -> String,
    extrasLabel: () -> String,
): TitleBarText {
    val show: String = item?.show?.trim().orEmpty()
    val title: String = item?.title?.trim().orEmpty()
    val episode: Int? = item?.episode

    val primary: String = if (show.isNotEmpty()) show else title

    if (show.isEmpty() || episode == null) {
        return TitleBarText(primary = primary, secondary = "")
    }

    // An episode whose title IS the show name carries no information, and
    // servers write that for untitled episodes. Printing it puts the show name
    // on both lines.
    val episodeTitle: String = if (title.isNotEmpty() && title != show) title else ""
    val season: Int? = item.season

    val label: String = when {
        season != null && season > 0 -> seasonLabel(season) + episodeLabel(episode)
        season == 0 -> extrasLabel() + " " + episodeLabel(episode)
        else -> episodeLabel(episode)
    }

    val secondary: String = if (episodeTitle.isNotEmpty()) "$label $SEPARATOR $episodeTitle" else label

    return TitleBarText(primary = primary, secondary = secondary)
}

// The web's own separator between the label and the episode title.
private const val SEPARATOR = "•"
