// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable

/**
 * The eight groups the web lists, in its order, with its keys.
 *
 * Every label goes through the locale table rather than being written in English:
 * the `shortcuts.*` keys are already translated for every locale the chrome has, and
 * this is the third thing in this package to have had strings available and unread.
 *
 * The KEYS are not translated. `Space`, `Shift` and the arrows are what is printed
 * on the keyboard, and a Dutch viewer's keyboard says the same thing.
 */
@Composable
public fun rememberShortcutGroups(locale: String = rememberChromeLocale()): List<ShortcutGroup> =
    shortcutGroups(locale)

/** The same table without a composition, for a gate or a test. */
public fun shortcutGroups(locale: String): List<ShortcutGroup> {
    val words = ShortcutWords(locale)

    return listOf(
        playbackGroup(words),
        speedGroup(words),
        volumeGroup(words),
        seekingGroup(words),
        quickSeekGroup(words),
        navigationGroup(words),
        tracksGroup(words),
        displayGroup(words),
    )
}

// The locale lookups as one object, so each group below reads as its own table
// rather than repeating the key prefix eight times.
private class ShortcutWords(private val locale: String) {
    fun label(name: String): String = ChromeTranslations.get(locale, "plugin.desktop-ui.shortcuts.$name")

    fun group(name: String): String = ChromeTranslations.get(locale, "plugin.desktop-ui.shortcuts.group.$name")
}

// Shift, Alt and Ctrl all pair with the same arrows, and Shift also carries the
// chapter pair. Named because a repeated literal in a table is a place for one of
// them to drift out of step with the others.
private const val ARROWS = "← / →"
private const val SHIFT = "Shift"

private fun playbackGroup(words: ShortcutWords): ShortcutGroup = ShortcutGroup(
    title = words.group("playback"),
    entries = listOf(
        ShortcutEntry(listOf("Space"), words.label("playPause")),
        ShortcutEntry(listOf("S"), words.label("stop")),
        ShortcutEntry(listOf("E"), words.label("frameAdvance")),
    ),
)

private fun speedGroup(words: ShortcutWords): ShortcutGroup = ShortcutGroup(
    title = words.group("speed"),
    entries = listOf(
        ShortcutEntry(listOf("]"), words.label("speedUp")),
        ShortcutEntry(listOf("["), words.label("speedDown")),
        ShortcutEntry(listOf("="), words.label("normalSpeed")),
    ),
)

private fun volumeGroup(words: ShortcutWords): ShortcutGroup = ShortcutGroup(
    title = words.group("volume"),
    entries = listOf(
        ShortcutEntry(listOf("↑"), words.label("volumeUp")),
        ShortcutEntry(listOf("↓"), words.label("volumeDown")),
        ShortcutEntry(listOf("M"), words.label("mute")),
    ),
)

private fun seekingGroup(words: ShortcutWords): ShortcutGroup = ShortcutGroup(
    title = words.group("seeking"),
    entries = listOf(
        ShortcutEntry(listOf("←"), words.label("seekBack5")),
        ShortcutEntry(listOf("→"), words.label("seekForward5")),
        ShortcutEntry(listOf(SHIFT, ARROWS), words.label("seek3s")),
        ShortcutEntry(listOf("Alt", ARROWS), words.label("seek10s")),
        ShortcutEntry(listOf("Ctrl", ARROWS), words.label("seek60s")),
    ),
)

private fun quickSeekGroup(words: ShortcutWords): ShortcutGroup = ShortcutGroup(
    title = words.group("quickSeek"),
    entries = listOf(
        ShortcutEntry(listOf("3"), words.label("seek30s")),
        ShortcutEntry(listOf("6"), words.label("seek60sKey")),
        ShortcutEntry(listOf("9"), words.label("seek90s")),
        ShortcutEntry(listOf("1"), words.label("seek120s")),
    ),
)

private fun navigationGroup(words: ShortcutWords): ShortcutGroup = ShortcutGroup(
    title = words.group("navigation"),
    entries = listOf(
        ShortcutEntry(listOf("N"), words.label("next")),
        ShortcutEntry(listOf("P"), words.label("previous")),
        ShortcutEntry(listOf(SHIFT, "N"), words.label("nextChapter")),
        ShortcutEntry(listOf(SHIFT, "P"), words.label("previousChapter")),
    ),
)

private fun tracksGroup(words: ShortcutWords): ShortcutGroup = ShortcutGroup(
    title = words.group("tracksAndSubtitles"),
    entries = listOf(
        ShortcutEntry(listOf("V"), words.label("cycleSubs")),
        ShortcutEntry(listOf("B"), words.label("cycleAudio")),
        ShortcutEntry(listOf("A"), words.label("cycleAspect")),
        ShortcutEntry(listOf("+"), words.label("subSizeUp")),
        ShortcutEntry(listOf("–"), words.label("subSizeDown")),
    ),
)

// F and F11 carry the same label on purpose: the web lists both keys against
// `shortcuts.fullscreen`, because both do it and a viewer looking for one may know
// only the other.
private fun displayGroup(words: ShortcutWords): ShortcutGroup = ShortcutGroup(
    title = words.group("display"),
    entries = listOf(
        ShortcutEntry(listOf("F"), words.label("fullscreen")),
        ShortcutEntry(listOf("F11"), words.label("fullscreen")),
        ShortcutEntry(listOf("Esc"), words.label("exitFullscreen")),
        ShortcutEntry(listOf("T"), words.label("showTime")),
        ShortcutEntry(listOf("?"), words.label("help")),
    ),
)
