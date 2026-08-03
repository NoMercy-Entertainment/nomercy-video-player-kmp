// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.item

// Where an autoplaying queue should start, and how far into it.
//
// [resumeSeconds] is null when it should start at the beginning, which is not
// the same as zero: the caller passes it as the load's start position and a
// zero would be a seek nobody asked for.
public data class StartSelection(
    val index: Int,
    val resumeSeconds: Double? = null,
)

// The continue-watching rule, ported from the reference's start-selection.
//
// Every input it needs was already here — [WatchProgress] carries the epoch,
// the position and the percentage, and [normalizeWatchProgress] accepts both
// wire shapes — and nothing read them to decide where playback began. An
// autoplaying native player started at the first item from zero, which is the
// continue-watching feature not working rather than a rule being slightly off.
//
// Three decisions, all viewer-visible:
//
//  - The most recently watched item wins, by timestamp. Not the first one with
//    progress and not the furthest through: a viewer's last session is what
//    "carry on" means.
//  - Past ninety percent it rolls over to the NEXT item and starts it fresh.
//    Resuming four minutes from the end of an episode somebody finished is the
//    case this exists for.
//  - Otherwise it resumes at the stored position.
//
// The ninety is a threshold rather than a rounding: an item at exactly ninety
// resumes, and only past it rolls over, which is what the reference's `>` does.
public fun pickStartItem(items: List<VideoPlaylistItem>): StartSelection {
    // Null timestamps sort last rather than first. A progress with no readable
    // date is the older shape whose date could not be parsed, and letting it
    // beat a session that DOES know when it happened would resume the wrong item.
    val mostRecent: VideoPlaylistItem? = items
        .filter { it.progress != null }
        .maxByOrNull { it.progress?.timestamp ?: Long.MIN_VALUE }

    val progress: WatchProgress = mostRecent?.progress ?: return StartSelection(index = 0)
    val index: Int = items.indexOf(mostRecent)

    return if (progress.percentage > ROLLOVER_PERCENT) {
        // Clamped, because the last item in a queue has nothing after it and
        // rolling past the end would leave nothing to play.
        StartSelection(index = minOf(index + 1, items.size - 1))
    } else {
        StartSelection(index = index, resumeSeconds = progress.time?.takeIf { it > 0.0 })
    }
}

private const val ROLLOVER_PERCENT: Double = 90.0
