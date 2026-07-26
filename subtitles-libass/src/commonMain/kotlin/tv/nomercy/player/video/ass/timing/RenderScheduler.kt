// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.timing

// When to render, and when not to.
//
// The decision half of the renderer. Compositing a bitmap is per-platform; the
// question of whether this millisecond needs a new one at all is the same
// everywhere, and it is the half that decides whether the subtitle layer costs
// a few percent of a frame budget or all of it.
//
// A static line held for four seconds is one render. Rendering it at 24fps
// instead is ninety-six identical bitmaps, and on a low-end television that is
// the difference between smooth playback and dropped frames.
public object RenderScheduler {

    // Karaoke moves between boundaries, so it gets a frame cadence. Twenty-four
    // a second is enough for a wipe to look continuous and cheap enough that a
    // television keeps up.
    public const val DYNAMIC_INTERVAL_MS: Long = 42L

    // Nothing on screen, or nothing that moves. Ten times a second is
    // imperceptible for an appearing line and costs almost nothing.
    public const val FALLBACK_INTERVAL_MS: Long = 100L

    // When this renderer should next be asked for a frame.
    //
    // A moving cue gets the frame cadence. A static one gets the moment it
    // changes, which may be seconds away and is exactly the saving. With
    // nothing scheduled at all, the fallback keeps the loop alive without
    // burning it.
    public fun nextRenderTime(model: AssTrackModel, currentMs: Long): Long {
        val active: List<AssCue> = model.activeCuesAt(currentMs)
        if (active.any { it.isDynamic }) return currentMs + DYNAMIC_INTERVAL_MS

        val boundary: Long? = model.nextBoundaryAfter(currentMs)
        return boundary ?: (currentMs + FALLBACK_INTERVAL_MS)
    }

    // What is on screen, as a value that changes when the picture should.
    //
    // Index and start time rather than the text: two cues with identical text at
    // different times are different frames, and comparing text alone would hold
    // a stale bitmap across a repeated line — which subtitles are full of.
    public fun fingerprintAt(model: AssTrackModel, timeMs: Long): String =
        model.activeCuesAt(timeMs).joinToString("|") { "${it.index}:${it.startMs}" }

    // Whether this millisecond needs a new frame.
    //
    // Three reasons, in the order they cost: the picture changed, a moving cue
    // is due another step, or the loop has been idle long enough to check
    // again. Anything else is a bitmap identical to the one already on screen.
    public fun isRenderNeeded(
        model: AssTrackModel,
        currentMs: Long,
        lastRenderedMs: Long,
        lastFingerprint: String,
    ): Boolean {
        if (fingerprintAt(model, currentMs) != lastFingerprint) return true

        val elapsed: Long = currentMs - lastRenderedMs
        // Backwards is a seek, and everything rendered ahead of it is wrong.
        if (elapsed < 0L) return true

        val dynamic: Boolean = model.activeCuesAt(currentMs).any { it.isDynamic }
        return if (dynamic) elapsed >= DYNAMIC_INTERVAL_MS else elapsed >= FALLBACK_INTERVAL_MS
    }

    // The upcoming moments worth rendering before they arrive.
    //
    // A cue appearing on the frame it is needed is a cue that appears late, by
    // however long libass took. Pre-rendering the next few boundaries moves that
    // cost into the gaps where there is nothing else to do.
    //
    // Bounded twice on purpose. A film has thousands of boundaries and a scan of
    // all of them is its own cost; the window and the frame cap keep the work
    // proportional to what is about to happen.
    public fun lookaheadBoundaries(
        model: AssTrackModel,
        currentMs: Long,
        aheadMs: Long = LOOKAHEAD_MS,
        maxFrames: Int = MAX_LOOKAHEAD_FRAMES,
    ): List<Long> =
        model.cues
            .flatMap { listOf(it.startMs, it.endMs) }
            .filter { it > currentMs && it <= currentMs + aheadMs }
            .distinct()
            .sorted()
            .take(maxFrames)

    private const val LOOKAHEAD_MS = 10_000L
    private const val MAX_LOOKAHEAD_FRAMES = 6
}
