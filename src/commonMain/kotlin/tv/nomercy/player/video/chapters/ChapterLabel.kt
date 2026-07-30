// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.chapters

import tv.nomercy.player.core.media.Chapter

// Which chapter a viewer is in, as a line they can read.
//
// The info panel on a television is the only place a viewer finds out where they
// are, and this side sent them the position and the runtime and nothing else. On
// a two-hour film with chapters that is a number of seconds where the browser
// says "Chapter 4: The Crossing".
//
// The LAST chapter that has started, not the first. Walked from the end because
// every chapter before the playhead satisfies "has started", and taking the first
// match names chapter one for the whole film.
//
// The word is passed in rather than spelled here. It is one of the nine strings
// the web's tv-key-handler translates, and it genuinely differs — Hoofdstuk,
// Kapitel, Chapitre — so a library that wrote "Chapter" would be a television
// speaking English in seventy-eight locales.
public fun resolveChapterLabel(
    chapters: List<Chapter>,
    currentTime: Double,
    chapterWord: String,
): String {
    // Empty rather than a word on its own. A film without chapters has no
    // chapter to name, and "Chapter" with no number reads as a bug.
    val active: Chapter = chapters.lastOrNull { currentTime >= it.startTime } ?: return ""

    // Counted from one. A viewer reading "Chapter 0" is reading an index.
    val number: Int = chapters.indexOf(active) + FIRST_CHAPTER_NUMBER

    return if (active.title.isEmpty()) {
        "$chapterWord $number"
    } else {
        "$chapterWord $number: ${active.title}"
    }
}

private const val FIRST_CHAPTER_NUMBER: Int = 1
