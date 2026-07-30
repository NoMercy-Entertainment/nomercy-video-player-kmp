// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

// What the chrome knows about what is playing.
//
// Deliberately not the player's own item. A title bar needs a show name and an
// episode number, and a playlist item carries a url and a duration; the two
// overlap by one field. Keeping them apart is what lets the chrome be given a
// title before anything has loaded.
public data class TvChromeItem(
    val title: String? = null,
    val show: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    // Two of them because the two sources disagree. A playlist says what kind of
    // list it is and an item says what kind of thing it is, and a special can
    // arrive labelled either way.
    val playlistType: String? = null,
    val videoType: String? = null,

    // What the playlist card draws, appended rather than woven in so a host
    // constructing this positionally keeps compiling.
    //
    // The card is the web's `buildPlaylistCard`, and every one of these is a
    // field it reads: the id is what a chosen row plays (`player.item(id)`),
    // and the rest fill the thumbnail's overlay and the text beside it. A
    // playlist menu built from title alone is a list of strings, which is what
    // the port drew.
    val id: String? = null,
    // `readItemImage`'s answer — image, then poster, then thumbnail, resolved
    // by whoever built this. Drawn through `ChromeSlots.artwork`, because this
    // library has no image loader and no business having one.
    val image: String? = null,
    val description: String? = null,
    val durationSeconds: Double? = null,
    // 0-100, the web's `progress.percentage`. Nothing here writes it: it is
    // continue-watching state the host persists and the card renders.
    val progressPercent: Int? = null,
)

// The name across the top.
//
// A show name where there is one, because on a television the row underneath
// carries the episode and repeating the show there reads as a stutter. A film
// has no show, so its own title goes here instead.
public fun showTitle(item: TvChromeItem?, loading: String): String {
    if (item == null) return loading

    return item.show?.takeIf { it.isNotBlank() }
        ?: item.title?.takeIf { it.isNotBlank() }
        ?: loading
}

// The line underneath: which episode this is, and what it is called.
//
// Branchy because the shapes genuinely differ, and this is where an extraction
// goes wrong quietly. Every arm produces something a viewer can read, and the
// failure mode is not a crash: it is "S0E4" on a collection, or an empty line
// where an episode number belonged.
public fun episodeLabel(item: TvChromeItem?, labels: EpisodeLabels = EpisodeLabels()): String {
    // A film is the empty case, and so is anything with a position but nothing
    // to hold that position within. Its name is already across the top, and a
    // second line repeating it is the stutter this avoids.
    if (item == null || item.show.isNullOrBlank()) return ""

    val number: String = numberOf(item, labels) ?: return ""
    val title: String? = item.title?.takeIf { it.isNotBlank() }

    return if (title == null) number else "$number • $title"
}

// Collections and specials have no seasons, so numbering them like a series
// produces a season that does not exist. They carry a position and nothing else.
private fun numberOf(item: TvChromeItem, labels: EpisodeLabels): String? {
    val episode: Int = item.episode ?: return null
    val season: Int? = item.season

    return when {
        isNonSeasonal(item) -> "$episode"
        season != null && season != 0 -> "${labels.season}$season${labels.episode}$episode"
        // Season zero is where a library puts everything that is not part of the
        // run itself. Calling it season zero to a viewer means nothing.
        season == 0 -> "${labels.extras} ${labels.episode}$episode"
        else -> null
    }
}

private fun isNonSeasonal(item: TvChromeItem): Boolean {
    val unseasoned: Boolean = item.season == null || item.season == 0
    val kind: Boolean = item.playlistType in NON_SEASONAL || item.videoType in NON_SEASONAL

    return unseasoned && kind
}

// Supplied rather than spelled here, because these are the shortest strings in
// the product and the ones most likely to differ per language. A season is not
// abbreviated to S everywhere.
public data class EpisodeLabels(
    val season: String = "S",
    val episode: String = "E",
    val extras: String = "Extras",
)

private val NON_SEASONAL = setOf("special", "collection")
