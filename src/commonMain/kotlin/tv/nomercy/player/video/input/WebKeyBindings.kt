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
/**
 * What a key does, with the distance in the name wherever the web writes one.
 *
 * The magnitudes are part of the action rather than a note beside it, because a
 * key bound to the right verb and the wrong number is the drift that survives
 * every check: `ArrowRight` seeking ten seconds instead of five is still
 * "forward", still fires, still looks correct in a diff, and is a different
 * player to use.
 */
public enum class KeyAction {
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    STOP,
    SEEK_BACK_5,
    SEEK_FORWARD_5,
    SEEK_BACK_3,
    SEEK_FORWARD_3,
    SEEK_BACK_10,
    SEEK_FORWARD_10,
    SEEK_BACK_60,
    SEEK_FORWARD_60,
    SEEK_FORWARD_30,
    SEEK_FORWARD_90,
    SEEK_FORWARD_120,
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
    CYCLE_ASPECT_RATIO,
    SPEED_UP,
    SPEED_DOWN,
    SPEED_RESET,
    FRAME_ADVANCE,
    SHOW_TIME,
    SUBTITLE_SIZE_UP,
    SUBTITLE_SIZE_DOWN,
    TOGGLE_SHORTCUTS,
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
    WebKeyBinding("MediaRewind", KeyAction.SEEK_BACK_5),
    WebKeyBinding("MediaFastForward", KeyAction.SEEK_FORWARD_5),

    // Navigation. On a television these move a highlight instead, which is the
    // player's own check rather than a different binding.
    //
    // Five seconds, not ten. The handler calls `rewind()` and `forward()` with
    // no argument and the player's own default is 5 — which is also what the
    // shortcuts panel prints. Ten is the ALT distance, one line below.
    WebKeyBinding("ArrowLeft", KeyAction.SEEK_BACK_5),
    WebKeyBinding("ArrowRight", KeyAction.SEEK_FORWARD_5),
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

    // The number row a set-top box remote carries. Four forward jumps, and the
    // numbers are not the seconds: `1` is the LONGEST at two minutes, because
    // it sits under the thumb and skipping an opening is what it is for.
    WebKeyBinding("1", KeyAction.SEEK_FORWARD_120),
    WebKeyBinding("3", KeyAction.SEEK_FORWARD_30),
    WebKeyBinding("6", KeyAction.SEEK_FORWARD_60),
    WebKeyBinding("9", KeyAction.SEEK_FORWARD_90),

    // The coloured buttons on a European remote. Absent from a keyboard and
    // present on every set-top box, which is exactly why they are easy to drop.
    //
    // The same four jumps as the numbers above, in the remote's own order —
    // red the shortest through blue the longest — so nobody has to learn two
    // sets. They are not four colour-named actions of their own.
    WebKeyBinding("ColorF0Red", KeyAction.SEEK_FORWARD_30),
    WebKeyBinding("ColorF1Green", KeyAction.SEEK_FORWARD_60),
    WebKeyBinding("ColorF2Yellow", KeyAction.SEEK_FORWARD_90),
    WebKeyBinding("ColorF3Blue", KeyAction.SEEK_FORWARD_120),

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

    // The VLC pair, and they are SPEED. Both of them read as chapter keys —
    // `[` and `]` are the bracket keys a media player uses for markers in half
    // the world's software — and they step along the rate list here.
    WebKeyBinding("]", KeyAction.SPEED_UP),
    WebKeyBinding("[", KeyAction.SPEED_DOWN),
    WebKeyBinding("=", KeyAction.SPEED_RESET),

    // One frame, and only while paused.
    WebKeyBinding("e", KeyAction.FRAME_ADVANCE),

    // Elapsed and remaining, as a message. Not theater mode: the web player has
    // no theater key at all, and `t` is the one a viewer presses to find out how
    // much is left without waking the whole chrome.
    WebKeyBinding("t", KeyAction.SHOW_TIME),

    // The size of the SUBTITLES, not the speed. An event rather than a call,
    // because whatever draws them owns how large they are.
    WebKeyBinding("+", KeyAction.SUBTITLE_SIZE_UP),
    WebKeyBinding("shift++", KeyAction.SUBTITLE_SIZE_UP),
    WebKeyBinding("-", KeyAction.SUBTITLE_SIZE_DOWN),

    WebKeyBinding("a", KeyAction.CYCLE_ASPECT_RATIO),
    WebKeyBinding("BrowserFavorites", KeyAction.CYCLE_ASPECT_RATIO),

    // Stop, which is the one key here that ends playback rather than changing it.
    WebKeyBinding("s", KeyAction.STOP),

    WebKeyBinding("shift+?", KeyAction.TOGGLE_SHORTCUTS),
)

/** Every combo the web binds, for asserting the native set covers it. */
public val WEB_KEY_COMBOS: Set<String> = WEB_KEY_BINDINGS.map { it.combo }.toSet()
