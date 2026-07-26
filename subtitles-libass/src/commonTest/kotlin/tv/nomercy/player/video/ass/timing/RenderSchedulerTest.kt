// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.timing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SCRIPT = """
[Script Info]
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, Alignment
Style: Default,Skeleton Sans,48,2

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:05.00,0:00:09.00,Default,,0,0,0,,A static line held for four seconds.
Dialogue: 0,0:00:11.00,0:00:15.00,Default,,0,0,0,,{\k30}Ka{\k25}ra{\k40}o{\k35}ke
""".trimIndent()

// When to render, and — the part that matters — when not to.
//
// A static line held for four seconds is one render. At 24fps it is
// ninety-six identical bitmaps, and on a low-end television that is the
// difference between smooth playback and dropped frames.
class RenderSchedulerTest {

    private val model: AssTrackModel = AssTrackModel.parse(SCRIPT)

    @Test
    fun aMovingCueGetsAFrameCadence() {
        // Karaoke changes between boundaries, so it needs stepping through.
        assertEquals(12_042L, RenderScheduler.nextRenderTime(model, 12_000L))
    }

    @Test
    fun aStaticCueIsScheduledForTheMomentItChanges() {
        // Seconds away, and that is the entire saving.
        assertEquals(9_000L, RenderScheduler.nextRenderTime(model, 6_000L))
    }

    @Test
    fun withNothingLeftTheLoopStillTicks() {
        // A fallback rather than never: the loop has to stay alive to notice a
        // seek back into the film.
        assertEquals(60_100L, RenderScheduler.nextRenderTime(model, 60_000L))
    }

    @Test
    fun aChangedPictureAlwaysNeedsAFrame() {
        val before: String = RenderScheduler.fingerprintAt(model, 6_000L)

        assertTrue(
            RenderScheduler.isRenderNeeded(model, currentMs = 12_000L, lastRenderedMs = 6_000L, lastFingerprint = before),
        )
    }

    @Test
    fun anUnchangedStaticPictureDoesNotNeedOne() {
        // The case the whole scheduler exists for: same line, a frame later,
        // nothing to draw.
        val fingerprint: String = RenderScheduler.fingerprintAt(model, 6_000L)

        assertTrue(
            !RenderScheduler.isRenderNeeded(model, 6_042L, lastRenderedMs = 6_000L, lastFingerprint = fingerprint),
        )
    }

    @Test
    fun anUnchangedStaticPictureIsRecheckedEventually() {
        // Not never. The fallback tick is what notices a boundary the schedule
        // missed, and a tenth of a second is imperceptible.
        val fingerprint: String = RenderScheduler.fingerprintAt(model, 6_000L)

        assertTrue(
            RenderScheduler.isRenderNeeded(model, 6_100L, lastRenderedMs = 6_000L, lastFingerprint = fingerprint),
        )
    }

    @Test
    fun anUnchangedDynamicPictureNeedsAFrameSooner() {
        // Same cue, same fingerprint, and it still has to be redrawn — the wipe
        // has moved even though which cue is on screen has not.
        val fingerprint: String = RenderScheduler.fingerprintAt(model, 12_000L)

        assertTrue(
            RenderScheduler.isRenderNeeded(model, 12_042L, lastRenderedMs = 12_000L, lastFingerprint = fingerprint),
        )
        assertTrue(
            !RenderScheduler.isRenderNeeded(model, 12_010L, lastRenderedMs = 12_000L, lastFingerprint = fingerprint),
        )
    }

    @Test
    fun aSeekBackwardsAlwaysNeedsAFrame() {
        // Everything rendered ahead of a seek is wrong, including a bitmap whose
        // fingerprint happens to match.
        val fingerprint: String = RenderScheduler.fingerprintAt(model, 6_000L)

        assertTrue(
            RenderScheduler.isRenderNeeded(model, 5_500L, lastRenderedMs = 6_000L, lastFingerprint = fingerprint),
        )
    }

    @Test
    fun theFingerprintChangesAcrossACueBoundary() {
        assertTrue(RenderScheduler.fingerprintAt(model, 6_000L) != RenderScheduler.fingerprintAt(model, 12_000L))
    }

    @Test
    fun twoIdenticalLinesAtDifferentTimesAreDifferentFrames() {
        // Subtitles repeat text constantly. A fingerprint made of the text alone
        // would hold a stale bitmap across a repeated line.
        val repeated: AssTrackModel = AssTrackModel.parse(
            SCRIPT.replace("""{\k30}Ka{\k25}ra{\k40}o{\k35}ke""", "A static line held for four seconds."),
        )

        assertTrue(
            RenderScheduler.fingerprintAt(repeated, 6_000L) != RenderScheduler.fingerprintAt(repeated, 12_000L),
        )
    }

    @Test
    fun anEmptyScreenHasAStableEmptyFingerprint() {
        // Two gaps must compare equal, or the renderer redraws nothing over and
        // over between every pair of lines.
        assertEquals(RenderScheduler.fingerprintAt(model, 10_000L), RenderScheduler.fingerprintAt(model, 10_500L))
    }

    @Test
    fun lookaheadReturnsUpcomingBoundariesOnly() {
        val boundaries: List<Long> = RenderScheduler.lookaheadBoundaries(model, 6_000L)

        assertTrue(boundaries.isNotEmpty())
        assertTrue(boundaries.all { it > 6_000L }, "the lookahead included a boundary already passed")
        assertEquals(boundaries.sorted(), boundaries)
    }

    @Test
    fun lookaheadStaysInsideItsWindow() {
        // A film has thousands of boundaries and scanning all of them is its own
        // cost. The window keeps the work proportional to what is about to
        // happen.
        val boundaries: List<Long> = RenderScheduler.lookaheadBoundaries(model, 0L, aheadMs = 6_000L)

        assertTrue(boundaries.all { it <= 6_000L })
    }

    @Test
    fun lookaheadIsCappedByFrameCountToo() {
        val boundaries: List<Long> = RenderScheduler.lookaheadBoundaries(model, 0L, maxFrames = 1)

        assertEquals(1, boundaries.size)
    }
}
