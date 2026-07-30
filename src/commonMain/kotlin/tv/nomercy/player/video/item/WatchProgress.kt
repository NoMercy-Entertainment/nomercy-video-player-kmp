// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.item

import kotlin.math.min
import kotlin.time.Instant

// How far a viewer got, as a server states it.
//
// TWO wire shapes in one type, which is what the web player's own loose item
// carries. The canonical one names an epoch and a percentage; the older one names
// a date string and a position and leaves the percentage to be worked out. Both
// are live: a self-hosted server is whatever version its owner installed, and
// dropping the old shape would make the continue-watching bar disappear on every
// library that has not updated.
//
// The player never writes this. Continue-watching state is the host's to persist
// and this is what it hands over.
public data class WatchProgress(
    /**
     * Unix epoch milliseconds of the last watch session. The canonical field, and
     * what tells the two shapes apart. Null on the older shape, where [date]
     * carries the same fact as a string.
     */
    val timestamp: Long? = null,
    /** 0 to 100. Zero is unwatched and a hundred is finished. */
    val percentage: Double = 0.0,
    /** Where to resume, in seconds. */
    val time: Double? = null,
    /**
     * The older shape's date, as the server wrote it. Read by
     * [normalizeWatchProgress] and dropped from what it returns, exactly as the
     * web does — the normalised value carries an epoch and nothing else, which is
     * also what makes running it twice a no-op.
     */
    val date: String? = null,
)

// Which of the two shapes this is.
//
// Both halves of the question, matching the web's `isLegacyProgress`: no epoch
// AND a date string. Testing only for the missing epoch would call a progress
// that carries neither "old" and then try to parse a date that is not there.
public fun WatchProgress.isLegacy(): Boolean = timestamp == null && date != null

// The older shape rewritten as the canonical one.
//
// Idempotent. Anything already canonical comes back untouched, so a host that
// normalises on ingest and a chrome that normalises on render cannot disagree —
// and the returned value never carries [date], so a second pass reads it as
// canonical rather than parsing the date again.
//
// The percentage is derived here because the old shape does not carry one, and it
// needs the item's length to derive it. An unknown length gives nought rather
// than a division by zero, which is the same rule the bar itself follows.
public fun normalizeWatchProgress(progress: WatchProgress?, durationSeconds: Double?): WatchProgress? {
    if (progress == null) return null
    if (!progress.isLegacy()) return progress

    val position: Double = progress.time ?: 0.0

    return WatchProgress(
        timestamp = epochMillisOf(progress.date),
        // Clamped at a hundred, because a stored position past a duration that
        // was later corrected downwards is ordinary and a bar wider than its own
        // track is not.
        percentage = watchedPercentOf(position, durationSeconds),
        time = position,
    )
}

private fun watchedPercentOf(position: Double, durationSeconds: Double?): Double {
    val known: Boolean = durationSeconds != null && durationSeconds > 0.0
    if (!known) return 0.0

    return min(FULLY_WATCHED, position / durationSeconds!! * FULLY_WATCHED)
}

// Null rather than a thrown parse error on a date nobody can read.
//
// The browser answers NaN here, which Kotlin has no equivalent of on a Long. Null
// says the same thing in the vocabulary this side has: the session's date is
// unknown. Nothing draws it — the host formats it — so an unreadable date costs
// the timestamp and not the progress bar.
// The opt-in is contained to this function on purpose. `kotlin.time.Instant` is
// still experimental on the Kotlin this builds against, and putting it in a
// public signature would push that opt-in onto every consumer. Nothing of it
// escapes: what comes back is a Long.
@OptIn(kotlin.time.ExperimentalTime::class)
private fun epochMillisOf(date: String?): Long? =
    date?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

private const val FULLY_WATCHED: Double = 100.0
