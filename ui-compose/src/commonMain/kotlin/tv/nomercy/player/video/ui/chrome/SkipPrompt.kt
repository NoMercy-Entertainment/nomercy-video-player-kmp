// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import tv.nomercy.player.video.tv.TvChapter

/**
 * Whether to offer a skip, and which one, from the app's own rules.
 *
 * Ported from `SkipUtils` in ChapterAutoSkipPlugin, pattern for pattern. The
 * button is a button; what decides whether it appears on the right chapter is
 * this, and getting it wrong is silent in both directions — a prompt that never
 * shows looks like a source with no chapters, and one that shows on the wrong
 * chapter offers to skip the film.
 *
 * The two guards matter as much as the patterns. An opening in the first half of
 * the FIRST item in a playlist is not offered, and an ending in the second half
 * of the LAST one is not either: on a single episode both would let somebody
 * skip past the only thing they came to watch, and at the ends of a playlist
 * there is nothing after to skip to.
 */
public object SkipPrompt {

    /** Which kind of segment a chapter title names, or none. */
    public fun typeOf(title: String): SkipKind? = when {
        INTRO.any { it.containsMatchIn(title) } -> SkipKind.Intro
        OUTRO.any { it.containsMatchIn(title) } -> SkipKind.Outro
        else -> null
    }

    /**
     * Whether this chapter should be offered at this point in this playlist.
     *
     * Both halves of the rule are the app's: no opening in the first half of the
     * first item, no ending in the second half of the last one.
     */
    public fun shouldOffer(title: String, at: SkipPosition): Boolean {
        if (at.isEarlyInTheFirstItem()) return false
        if (at.isLateInTheLastItem()) return false

        return typeOf(title) != null
    }

    /** The chapter the film is inside, if any. Inclusive at both ends, as there. */
    public fun chapterAt(chapters: List<TvChapter>, seconds: Double): TvChapter? =
        chapters.firstOrNull { seconds >= it.startSeconds && seconds <= endOf(chapters, it) }

    // TvChapter carries a start and no end, so a chapter runs until the next one
    // begins. The app's ChapterSegment carries both; this derives the same thing
    // rather than inventing a duration, and the last chapter runs to the end.
    private fun endOf(chapters: List<TvChapter>, chapter: TvChapter): Double =
        chapters.filter { it.startSeconds > chapter.startSeconds }
            .minOfOrNull { it.startSeconds }
            ?: Double.MAX_VALUE

    /**
     * How long the prompt stays up before it gives up, in milliseconds.
     *
     * Ten seconds, which is the app's figure and long enough to react to without
     * reading. After that the chapter is suppressed so it does not reappear every
     * frame for the rest of the segment.
     */
    public const val VISIBLE_MS: Long = 10_000

    /**
     * How close two chapter starts must be to count as the same one.
     *
     * Half a second, as there. The suppression is keyed on a start time and a
     * float that arrived by two different routes is not bit-identical, so an
     * exact comparison would fail to suppress and the prompt would flicker back.
     */
    public const val SAME_CHAPTER_SECONDS: Double = 0.5

    private val INTRO: List<Regex> = listOf(
        "^OP$",
        "^NCOP$",
        "^Opening$",
        "^Opening",
        "^Opening Credits$",
        "^Opening Theme$",
        "^Opening Song$",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val OUTRO: List<Regex> = listOf(
        "^ED$",
        "^PV$",
        "^NCED$",
        "^CM$",
        "^Preview$",
        "^Next Episode Preview$",
        "^Next Time Preview$",
        "^Outro$",
        "^Ending$",
        "^OP\\+Cast$",
        "^ED\\+Cast$",
        "^Credits$",
        "^End Credits$",
        "^Closing$",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }
}

/**
 * Where the film is, and where the item sits in the queue.
 *
 * One type rather than four parameters because the two guards each read three
 * of them and a call site passing them in the wrong order would compile — an
 * index and a playlist size are both bare Ints.
 */
public data class SkipPosition(
    val durationSeconds: Double,
    val currentSeconds: Double,
    val index: Int,
    val playlistSize: Int,
) {

    // Nothing is early or late in an item whose length is not known yet, which
    // is every item for the first moment after it loads.
    private val known: Boolean get() = durationSeconds > 0.0

    private val half: Double get() = durationSeconds / 2.0

    // Skipping an opening in the first half of the FIRST item is skipping the
    // start of the only thing playing.
    internal fun isEarlyInTheFirstItem(): Boolean =
        known && currentSeconds < half && index == 0

    // And an ending in the second half of the LAST one has nothing after it to
    // skip to.
    internal fun isLateInTheLastItem(): Boolean =
        known && currentSeconds > half && index == playlistSize - 1
}

/**
 * Which end of the item a skip belongs to.
 *
 * It decides where the button sits and which way it slides in — the app puts an
 * intro prompt bottom-start and an outro prompt bottom-end, so the two never
 * appear in the same place and a viewer learns where to look.
 */
public enum class SkipKind {
    Intro,
    Outro,
}

/**
 * The button itself: one line, one press, at the corner its kind belongs to.
 *
 * Deliberately plain. His is a Material button in the app's theme, and a library
 * that shipped Material would put a design system into every consumer's build —
 * so this is the same shape on foundation, and a host that wants its own
 * replaces it through the overlays slot.
 */
@Composable
public fun SkipButton(
    kind: SkipKind,
    label: String,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(SKIP_INSET)
            .background(SKIP_BACKGROUND, RoundedCornerShape(SKIP_RADIUS))
            .clickable(onClick = onSkip)
            .padding(horizontal = SKIP_PADDING_H, vertical = SKIP_PADDING_V)
            .testTag(if (kind == SkipKind.Intro) SKIP_INTRO_TAG else SKIP_OUTRO_TAG),
    ) {
        BasicText(text = label, style = SKIP_LABEL)
    }
}

internal const val SKIP_INTRO_TAG = "nm-skip-intro"
internal const val SKIP_OUTRO_TAG = "nm-skip-outro"

// Clear of the transport row, which is what his 120dp bottom inset is for: a
// prompt sitting on the controls is one a thumb hits while reaching for pause.
private val SKIP_INSET = 24.dp
private val SKIP_RADIUS = 6.dp
private val SKIP_PADDING_H = 20.dp
private val SKIP_PADDING_V = 12.dp

private val SKIP_BACKGROUND = Color(red = 20, green = 20, blue = 25, alpha = 242)

private val SKIP_LABEL = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
