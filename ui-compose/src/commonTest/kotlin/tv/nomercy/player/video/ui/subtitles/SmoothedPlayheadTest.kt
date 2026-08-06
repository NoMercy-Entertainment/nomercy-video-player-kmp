// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MS = 1_000_000L

class SmoothedPlayheadTest {

    // The defect this exists for: libVLC reports a position a few times a
    // second, so a cue drawn straight from it jumps rather than slides.
    @Test
    fun `the position advances between two identical reports`() {
        val playhead = SmoothedPlayhead()

        playhead.positionAt(1_000, 0)
        playhead.positionAt(1_250, 250 * MS)

        val eightMsLater: Long = playhead.positionAt(1_250, 258 * MS)
        val sixteenMsLater: Long = playhead.positionAt(1_250, 266 * MS)

        assertEquals(1_258, eightMsLater, "the playhead stood still between engine reports")
        assertEquals(1_266, sixteenMsLater)
    }

    @Test
    fun `a fresh report re-anchors the position`() {
        val playhead = SmoothedPlayhead()

        playhead.positionAt(1_000, 0)
        playhead.positionAt(1_100, 100 * MS)

        assertEquals(1_500, playhead.positionAt(1_500, 200 * MS))
    }

    // Without a bound this walks the subtitles off into a part of the film
    // nobody is watching, because a paused engine reports the same position for
    // as long as it is paused.
    @Test
    fun `a paused engine stops the position rather than running away`() {
        val playhead = SmoothedPlayhead()

        playhead.positionAt(1_000, 0)
        playhead.positionAt(1_200, 200 * MS)

        val afterTenSeconds: Long = playhead.positionAt(1_200, 10_200 * MS)

        assertTrue(
            afterTenSeconds <= 1_200 + 500,
            "ten seconds paused moved the playhead to $afterTenSeconds",
        )
    }

    // A cue that goes back re-runs a wipe the viewer already watched.
    @Test
    fun `the position never goes backwards while the report holds`() {
        val playhead = SmoothedPlayhead()

        playhead.positionAt(1_000, 0)
        playhead.positionAt(1_200, 200 * MS)

        val far: Long = playhead.positionAt(1_200, 900 * MS)
        val near: Long = playhead.positionAt(1_200, 300 * MS)

        assertTrue(near >= far, "the playhead went backwards, from $far to $near")
    }

    @Test
    fun `a seek backwards is followed rather than clamped`() {
        val playhead = SmoothedPlayhead()

        playhead.positionAt(60_000, 0)
        playhead.positionAt(60_200, 200 * MS)

        assertEquals(5_000, playhead.positionAt(5_000, 400 * MS))
    }
}
