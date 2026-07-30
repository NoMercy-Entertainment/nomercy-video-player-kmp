// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Both wire shapes, because both are live.
//
// A self-hosted server is whatever version its owner installed. The older shape
// sends a date and a position and no percentage, so a reader that only understood
// the canonical one drew every part-watched episode as untouched — and nothing
// throws, so the library owner sees a continue-watching row that has quietly
// stopped continuing.
class WatchProgressTest {

    @Test
    fun theOlderShapeIsTheOneWithADateAndNoEpoch() {
        assertTrue(WatchProgress(date = "2026-07-30T09:00:00Z", time = 60.0).isLegacy())
    }

    @Test
    fun anEpochMakesItCanonicalEvenWhenADateCameAlongToo() {
        // Both fields present is a server mid-migration. The epoch is the
        // canonical one, so it wins and no date is parsed.
        val progress = WatchProgress(timestamp = 111L, date = "2026-07-30T09:00:00Z")

        assertFalse(progress.isLegacy())
    }

    @Test
    fun neitherFieldIsNotTheOlderShape() {
        // A host that built this by hand and filled in only the percentage. Read
        // as legacy it would go looking for a date that is not there.
        assertFalse(WatchProgress(percentage = 40.0).isLegacy())
    }

    @Test
    fun theOlderShapeGetsItsPercentageWorkedOutFromTheRuntime() {
        val normalized: WatchProgress? = normalizeWatchProgress(
            WatchProgress(date = "2026-07-30T09:00:00Z", time = 300.0),
            durationSeconds = 1200.0,
        )

        assertEquals(25.0, normalized?.percentage)
        assertEquals(300.0, normalized?.time)
    }

    @Test
    fun theDateBecomesAnEpochAndIsNotCarriedForward() {
        val normalized: WatchProgress? = normalizeWatchProgress(
            WatchProgress(date = "1970-01-01T00:00:01Z", time = 1.0),
            durationSeconds = 100.0,
        )

        assertEquals(1_000L, normalized?.timestamp)
        // Dropped, as the web drops it. Carrying it forward would make a second
        // pass read the value as legacy again and re-derive everything.
        assertNull(normalized?.date)
    }

    @Test
    fun normalisingTwiceChangesNothing() {
        val once: WatchProgress? = normalizeWatchProgress(
            WatchProgress(date = "2026-07-30T09:00:00Z", time = 300.0),
            durationSeconds = 1200.0,
        )

        assertEquals(once, normalizeWatchProgress(once, durationSeconds = 1200.0))
    }

    @Test
    fun aPositionPastTheRuntimeStopsAtAHundred() {
        // A stored position against a duration later corrected downwards. Left
        // unclamped it draws a bar wider than its own track.
        val normalized: WatchProgress? = normalizeWatchProgress(
            WatchProgress(date = "2026-07-30T09:00:00Z", time = 5000.0),
            durationSeconds = 1200.0,
        )

        assertEquals(100.0, normalized?.percentage)
    }

    @Test
    fun anUnknownRuntimeReportsNoughtRatherThanDividingByZero() {
        val normalized: WatchProgress? = normalizeWatchProgress(
            WatchProgress(date = "2026-07-30T09:00:00Z", time = 300.0),
            durationSeconds = null,
        )

        assertEquals(0.0, normalized?.percentage)
    }

    @Test
    fun aCanonicalValuePassesThroughUntouched() {
        val canonical = WatchProgress(timestamp = 999L, percentage = 40.0, time = 480.0)

        assertEquals(canonical, normalizeWatchProgress(canonical, durationSeconds = 1200.0))
    }

    @Test
    fun anUnreadableDateCostsTheTimestampAndNotTheProgress() {
        // The browser answers NaN here. Null says the same thing with a Long, and
        // the bar still draws: nothing on screen reads the timestamp.
        val normalized: WatchProgress? = normalizeWatchProgress(
            WatchProgress(date = "last tuesday", time = 300.0),
            durationSeconds = 1200.0,
        )

        assertNull(normalized?.timestamp)
        assertEquals(25.0, normalized?.percentage)
    }

    @Test
    fun noProgressIsNoProgress() {
        assertNull(normalizeWatchProgress(null, durationSeconds = 1200.0))
    }
}
