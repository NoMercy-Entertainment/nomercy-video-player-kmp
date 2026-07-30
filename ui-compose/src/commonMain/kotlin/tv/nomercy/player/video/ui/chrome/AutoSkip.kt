// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.video.tv.TvChapter

/**
 * Whether openings and endings are skipped for the viewer or offered to them.
 *
 * `ChapterAutoSkipPlugin` reads this out of the app's own settings store and the
 * two halves are exclusive: `if (autoSkipEnabled) return` sits at the top of the
 * composable that draws the button. On, the plugin seeks past the chapter and
 * there is no button; off, the button appears and the viewer decides.
 *
 * The port had only the button, always. A viewer who had asked for openings to
 * be skipped got a prompt instead — which is not a smaller version of what they
 * asked for, it is the thing they turned off.
 *
 * The preference itself is not the player's to keep. It is one setting across
 * every item a viewer ever opens, it lives in their account, and a library that
 * stored it would be storing it somewhere the app cannot read. So it arrives as
 * a value and leaves through [onChange], and the app writes it where it already
 * writes the rest.
 */
public data class AutoSkipPreference(
    val enabled: Boolean = false,
    val onChange: (Boolean) -> Unit = {},
)

/**
 * Whether the right-hand clock reads what is left or how long the item is.
 *
 * The same shape as [AutoSkipPreference] and here for the same reason: the web
 * persists this under `showRemaining` and defaults it on, it is one choice across
 * every item a viewer opens, and a library that stored it would store it where
 * the app cannot read it. So it arrives as a value and leaves through [onChange].
 *
 * The port drew what-is-left with no way to reach the other reading at all — the
 * web's remaining-time element is a button, and clicking it switches.
 */
public data class ClockPreference(
    val showRemaining: Boolean = true,
    val onChange: (Boolean) -> Unit = {},
)

/**
 * The chapters this item has already been carried past.
 *
 * Without it an auto-skip fights the viewer: they scrub back into the opening
 * they just skipped, the rule fires again, and they cannot watch it. His plugin
 * keeps `autoSkippedStarts` for exactly that, and deliberately does NOT clear it
 * when the chapter list is replaced mid-item — a server that re-sends chapters
 * would otherwise re-arm every skip under a viewer who had scrubbed back on
 * purpose. It clears on the item, which is [forget].
 */
public class AutoSkipTracker {

    private val skipped: MutableSet<Double> = mutableSetOf()

    /** A new item, so nothing has been skipped yet. */
    public fun forget() {
        skipped.clear()
    }

    /**
     * Where to seek to leave the chapter under [position], or null to stay put.
     *
     * Null covers every reason not to move, and they are all ordinary: no
     * chapter here, a chapter that is not an opening or an ending, one already
     * skipped once, and the last chapter of the last item — which has nothing
     * after it to seek to and would strand playback at the duration.
     */
    public fun targetFor(chapters: List<TvChapter>, position: SkipPosition): Double? {
        val chapter: TvChapter = skippableAt(chapters, position) ?: return null

        val target: Double? = chapters
            .map { it.startSeconds }
            .filter { it > position.currentSeconds }
            .minOrNull()

        // Put it back when there is nowhere to go, so the chapter is still
        // skippable if the item gains a chapter after it.
        if (target == null) skipped.remove(chapter.startSeconds)

        return target
    }

    // The chapter under the playhead, if it is one to skip and has not been
    // skipped already. Marks it on the way out, so a caller that finds somewhere
    // to seek has nothing left to remember.
    private fun skippableAt(chapters: List<TvChapter>, position: SkipPosition): TvChapter? =
        SkipPrompt.chapterAt(chapters, position.currentSeconds)
            ?.takeIf { it.title != null }
            ?.takeIf { SkipPrompt.shouldOffer(it.title.orEmpty(), position) }
            ?.takeIf { skipped.add(it.startSeconds) }
}
