// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.ports.QualityLevel

/**
 * The labels that carry a VALUE, not just a name.
 *
 * `iconStateMethods.ts` does two things per control and only one of them was ported.
 * The glyph swaps — theater, pip, subtitles, fullscreen — were all here and correct.
 * The aria-label enrichment was not: every control announced a static noun, so the
 * speed button said "Speed" at 1.5× and the quality button said "Quality" while
 * playing 1080p.
 *
 * That was invisible as an accessibility gap and became visible the moment tooltips
 * landed, because the tooltip reads the same description. A viewer hovering speed on
 * the web sees the rate; here they saw the word.
 */

/**
 * `applyRate`: the base label, and the rate in brackets when it is not 1.
 *
 * Only when it differs, which is the web's condition. "Speed (1×)" on every ordinary
 * playback is noise, and a screen reader would read it on every focus.
 */
internal fun speedLabel(base: String, rate: Float): String =
    if (rate == NORMAL_RATE) base else "$base (${trimRate(rate)}$RATE_SUFFIX)"

/**
 * `applyQualityIcon`: the base label, and what is actually PLAYING after it.
 *
 * Playing, not selected. On an adaptive ladder those differ constantly — a viewer who
 * picked Auto still wants to know what they are getting, and announcing the selection
 * would say "Auto" forever.
 */
internal fun qualityLabel(base: String, playing: String?): String =
    if (playing == null) base else "$base: $playing"

/**
 * How the web writes a rate: `1.5×`, and `2×` rather than `2.0×`.
 *
 * JS prints a whole number without its decimal and Kotlin's Float does not, so
 * without this a 2× speed reads "2.0×" against the web's "2×" — a difference a parity
 * check on the string would catch and a human would call a typo.
 */
private fun trimRate(rate: Float): String {
    val whole: Int = rate.toInt()

    return if (rate == whole.toFloat()) whole.toString() else rate.toString()
}

private const val NORMAL_RATE = 1f

// The multiplication sign the web uses, not the letter x.
private const val RATE_SUFFIX = "×"

/**
 * How the web names a rung: its own label, or its height with a `p`.
 *
 * `level.label ?? (level.height ? `${level.height}p` : undefined)`. The label is
 * whatever the manifest called it and wins when present, because a server that names
 * a rung "1080p HDR" means it.
 */
internal fun QualityLevel.describe(): String? = label ?: height.takeIf { it > 0 }?.let { "${it}p" }
