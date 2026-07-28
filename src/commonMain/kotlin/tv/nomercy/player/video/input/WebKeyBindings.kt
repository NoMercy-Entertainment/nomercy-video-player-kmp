// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

/**
 * Every key the web player binds, and what it does.
 *
 * Extracted from `plugins/key-handler/index.ts` rather than remembered. Fifty-
 * three bindings, and the interesting half is the part nobody would guess: the
 * media keys a remote sends, the coloured buttons on a European remote, the
 * VLC-style modifier seeks, and the three separate keys that each cycle
 * subtitles because three different habits exist.
 *
 * A table rather than a `when` block, so the native side registers the same set
 * and a test can assert the set rather than assert one binding at a time. A
 * shortcut nobody ported is invisible until somebody presses it and nothing
 * happens, which is the least reportable kind of bug: it feels like the key
 * was never meant to work.
 */
public enum class KeyAction {
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    STOP,
    REWIND,
    FORWARD,
    SEEK_BACK_3,
    SEEK_FORWARD_3,
    SEEK_BACK_10,
    SEEK_FORWARD_10,
    SEEK_BACK_60,
    SEEK_FORWARD_60,
    VOLUME_UP,
    VOLUME_DOWN,
    TOGGLE_MUTE,
    CYCLE_SUBTITLES,
    CYCLE_AUDIO,
    NEXT_ITEM,
    PREVIOUS_ITEM,
    NEXT_CHAPTER,
    PREVIOUS_CHAPTER,
    TOGGLE_FULLSCREEN,
    EXIT_FULLSCREEN,
    TOGGLE_THEATER,
    CYCLE_ASPECT_RATIO,
    SPEED_UP,
    SPEED_DOWN,
    SPEED_RESET,
    TOGGLE_SETTINGS,
    TOGGLE_SHORTCUTS,
    TOGGLE_PIP,
    RED,
    GREEN,
    YELLOW,
    BLUE,
}

/**
 * The combo string exactly as the web writes it, so the two can be compared
 * without a translation step: modifiers lowercase and `+`-joined, then the key.
 */
public data class WebKeyBinding(val combo: String, val action: KeyAction)

/**
 * The web's table, in the order `index.ts` binds it.
 *
 * Grouped the way that file groups it — media keys, navigation, volume, track
 * cycling, modifier seeks, then the rest — because the grouping is where the
 * reasoning lives. Three keys cycle subtitles and three cycle audio: the remote
 * key, a number, and a letter. Dropping two of each looks like tidying and is
 * how a viewer's habit stops working.
 */
public val WEB_KEY_BINDINGS: List<WebKeyBinding> = listOf(
    // The spacebar, first in the web's list and the most-pressed key in the
    // player. It went missing from the first draft of this table precisely
    // because a bare " " does not read as a binding when you are skimming for
    // names — which is the argument for the table existing at all.
    WebKeyBinding(" ", KeyAction.PLAY_PAUSE),

    // What a remote or a keyboard's media row sends.
    WebKeyBinding("MediaPlay", KeyAction.PLAY),
    WebKeyBinding("MediaPause", KeyAction.PAUSE),
    WebKeyBinding("MediaPlayPause", KeyAction.PLAY_PAUSE),
    WebKeyBinding("MediaStop", KeyAction.STOP),
    WebKeyBinding("MediaRewind", KeyAction.REWIND),
    WebKeyBinding("MediaFastForward", KeyAction.FORWARD),

    // Navigation. On a television these move a highlight instead, which is the
    // player's own check rather than a different binding.
    WebKeyBinding("ArrowLeft", KeyAction.REWIND),
    WebKeyBinding("ArrowRight", KeyAction.FORWARD),
    WebKeyBinding("ArrowUp", KeyAction.VOLUME_UP),
    WebKeyBinding("ArrowDown", KeyAction.VOLUME_DOWN),
    WebKeyBinding("m", KeyAction.TOGGLE_MUTE),

    // Three each, because three habits exist: the dedicated remote key, the
    // number a set-top box uses, and the letter a keyboard user learns.
    WebKeyBinding("Subtitle", KeyAction.CYCLE_SUBTITLES),
    WebKeyBinding("5", KeyAction.CYCLE_SUBTITLES),
    WebKeyBinding("v", KeyAction.CYCLE_SUBTITLES),
    WebKeyBinding("Audio", KeyAction.CYCLE_AUDIO),
    WebKeyBinding("2", KeyAction.CYCLE_AUDIO),
    WebKeyBinding("b", KeyAction.CYCLE_AUDIO),

    // VLC-style: shift is 3 seconds, alt is 10, ctrl is 60.
    WebKeyBinding("shift+ArrowLeft", KeyAction.SEEK_BACK_3),
    WebKeyBinding("shift+ArrowRight", KeyAction.SEEK_FORWARD_3),
    WebKeyBinding("alt+ArrowLeft", KeyAction.SEEK_BACK_10),
    WebKeyBinding("alt+ArrowRight", KeyAction.SEEK_FORWARD_10),
    WebKeyBinding("ctrl+ArrowLeft", KeyAction.SEEK_BACK_60),
    WebKeyBinding("ctrl+ArrowRight", KeyAction.SEEK_FORWARD_60),

    // The number row a set-top box remote carries.
    WebKeyBinding("1", KeyAction.PREVIOUS_CHAPTER),
    WebKeyBinding("3", KeyAction.NEXT_CHAPTER),
    WebKeyBinding("6", KeyAction.CYCLE_ASPECT_RATIO),
    WebKeyBinding("9", KeyAction.TOGGLE_SETTINGS),

    // The coloured buttons on a European remote. Absent from a keyboard and
    // present on every set-top box, which is exactly why they are easy to drop.
    WebKeyBinding("ColorF0Red", KeyAction.RED),
    WebKeyBinding("ColorF1Green", KeyAction.GREEN),
    WebKeyBinding("ColorF2Yellow", KeyAction.YELLOW),
    WebKeyBinding("ColorF3Blue", KeyAction.BLUE),

    // Queue movement, remote and keyboard.
    WebKeyBinding("MediaTrackNext", KeyAction.NEXT_ITEM),
    WebKeyBinding("MediaTrackPrevious", KeyAction.PREVIOUS_ITEM),
    WebKeyBinding("n", KeyAction.NEXT_ITEM),
    WebKeyBinding("p", KeyAction.PREVIOUS_ITEM),
    WebKeyBinding("shift+n", KeyAction.NEXT_CHAPTER),
    WebKeyBinding("shift+p", KeyAction.PREVIOUS_CHAPTER),

    // Presentation.
    WebKeyBinding("f", KeyAction.TOGGLE_FULLSCREEN),
    WebKeyBinding("F11", KeyAction.TOGGLE_FULLSCREEN),
    WebKeyBinding("Escape", KeyAction.EXIT_FULLSCREEN),
    WebKeyBinding("]", KeyAction.NEXT_CHAPTER),
    WebKeyBinding("[", KeyAction.PREVIOUS_CHAPTER),
    WebKeyBinding("=", KeyAction.SPEED_RESET),
    WebKeyBinding("e", KeyAction.TOGGLE_PIP),
    WebKeyBinding("t", KeyAction.TOGGLE_THEATER),
    WebKeyBinding("+", KeyAction.SPEED_UP),
    WebKeyBinding("shift++", KeyAction.SPEED_UP),
    WebKeyBinding("-", KeyAction.SPEED_DOWN),
    WebKeyBinding("a", KeyAction.CYCLE_ASPECT_RATIO),
    WebKeyBinding("BrowserFavorites", KeyAction.TOGGLE_SETTINGS),
    WebKeyBinding("s", KeyAction.TOGGLE_SETTINGS),
    WebKeyBinding("shift+?", KeyAction.TOGGLE_SHORTCUTS),
)

/** Every combo the web binds, for asserting the native set covers it. */
public val WEB_KEY_COMBOS: Set<String> = WEB_KEY_BINDINGS.map { it.combo }.toSet()
