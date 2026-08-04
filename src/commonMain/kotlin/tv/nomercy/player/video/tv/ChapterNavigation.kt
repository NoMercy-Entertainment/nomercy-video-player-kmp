// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

/**
 * Where the chapter buttons go, exactly as the web decides it.
 *
 * From `desktop-ui/helpers/chapters.ts`. Written out because the first native
 * version got both rules wrong in ways that only show up under a finger:
 *
 * - The grace is **one second**, not three, and it works the way a CD player
 *   works rather than the way it first reads. More than a second into a
 *   chapter, back RESTARTS that chapter. Within a second of its start, back
 *   goes to the previous one. So pressing back twice in quick succession moves
 *   back a chapter, and pressing it once after a while restarts what you are
 *   watching. A three-second window makes the second press land in the same
 *   place as the first.
 * - Forward **does nothing** past the last boundary. The obvious alternative,
 *   seeking to the end, turns a dead button into one that ends the episode.
 */

/**
 * Where back goes: the start of the current chapter when more than a second
 * into it, the previous chapter's start when within that second, and **null**
 * when there is no boundary behind this position at all.
 *
 * Null rather than 0, which is what it used to answer. A back button with a
 * target is a back button that is never disabled, and the web disables it —
 * walked backwards through Sintel in a browser it stays live at 745, 621, 557,
 * 445, 338, 207 and 107 and goes `aria-disabled="true"` at 0, and an item with
 * no chapters at all has both directions disabled from the first frame.
 */
public fun previousChapterStart(starts: List<Double>, timeSeconds: Double): Double? =
    starts.sorted().lastOrNull { it < timeSeconds - CHAPTER_GRACE_SECONDS }

/**
 * The chapter to jump forward to, or null when the position is past the last
 * boundary — which is a no-op rather than a seek to the end.
 */
public fun nextChapterStart(starts: List<Double>, timeSeconds: Double): Double? =
    starts.sorted().firstOrNull { it > timeSeconds + CHAPTER_GRACE_SECONDS }

/**
 * One second either side. The web's number, and the reason both directions
 * have it: a press landing exactly on a boundary should move, not stay.
 */
public const val CHAPTER_GRACE_SECONDS: Double = 1.0
