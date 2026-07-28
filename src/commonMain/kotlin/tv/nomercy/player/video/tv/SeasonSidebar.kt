// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

/**
 * Whether the playlist menu shows a seasons rail or a flat list.
 *
 * The web player's rule, ported rather than reinvented, from
 * `desktop-ui/helpers/menus.ts shouldShowSeasonSidebar`:
 *
 * > Returns true only when the queue contains two or more distinct season
 * > numbers >= 1 among non-movie items. Every other case — season-0 specials,
 * > a single season, a movie collection — returns false and renders a flat list.
 *
 * It is a function rather than an inline condition because it is exactly the
 * rule that gets rewritten from memory as "show seasons if there is more than
 * one", which is wrong in three ways at once: it counts specials, it counts
 * movies, and it shows a rail for a single season whose episodes would fit the
 * full width.
 *
 * Two of the three exclusions are invisible in a screenshot. A collection of
 * films has no seasons to show and a naive rule shows an empty rail; a run of
 * specials is all season 0 and a naive rule shows a rail with one button on it.
 * Both look like a layout bug to whoever finds them and neither is.
 */
public fun shouldShowSeasonSidebar(queue: List<TvChromeItem>): Boolean =
    queue
        .filter { it.videoType != MOVIE && it.playlistType != MOVIE }
        .mapNotNull { it.season }
        .filter { it >= FIRST_REAL_SEASON }
        .distinct()
        .size >= MINIMUM_SEASONS

/**
 * The seasons a rail would offer, in order. Empty when the rail is not shown,
 * so a caller cannot draw one from a list this says is flat.
 */
public fun sidebarSeasons(queue: List<TvChromeItem>): List<Int> {
    if (!shouldShowSeasonSidebar(queue)) return emptyList()

    return queue
        .filter { it.videoType != MOVIE && it.playlistType != MOVIE }
        .mapNotNull { it.season }
        .filter { it >= FIRST_REAL_SEASON }
        .distinct()
        .sorted()
}

// The two fields disagree by design: a playlist says what kind of list it is
// and an item says what kind of thing it is, and a special can arrive labelled
// either way. The web checks both, so this does.
private const val MOVIE = "movie"

// Season 0 is the specials bucket. It is a real season number and not one a
// viewer navigates by, which is why the web excludes it here and shows those
// items in the flat list instead.
private const val FIRST_REAL_SEASON = 1

private const val MINIMUM_SEASONS = 2
