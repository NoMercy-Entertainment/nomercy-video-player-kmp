// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The table is the port's checklist, so what is asserted is that it still
// carries the parts nobody would guess were there.
//
// A shortcut nobody ported is invisible until somebody presses it and nothing
// happens, which is the least reportable kind of bug: it feels like the key was
// never meant to work, so it does not get reported at all.
class WebKeyBindingsTest {

    @Test
    fun theWholeWebSetIsHere() {
        assertEquals(EXPECTED_BINDINGS, WEB_KEY_BINDINGS.size)
    }

    // The spacebar is first in the web's list and the most-pressed key in the
    // player, and it was the one this table dropped on its first draft: a bare
    // " " does not read as a binding when you are skimming a list for names.
    @Test
    fun theSpacebarIsBound() {
        assertTrue(" " in WEB_KEY_COMBOS, "the spacebar is not bound")
        assertEquals(
            KeyAction.PLAY_PAUSE,
            WEB_KEY_BINDINGS.first { it.combo == " " }.action,
        )
    }

    @Test
    fun noComboIsBoundTwice() {
        val duplicates: List<String> = WEB_KEY_BINDINGS
            .groupBy { it.combo }
            .filterValues { it.size > 1 }
            .keys
            .toList()

        assertTrue(duplicates.isEmpty(), "bound twice: $duplicates")
    }

    // The coloured buttons are on every European set-top box and no keyboard,
    // which is exactly why they are the first thing a port drops.
    @Test
    fun theColouredRemoteButtonsSurvive() {
        for (combo in listOf("ColorF0Red", "ColorF1Green", "ColorF2Yellow", "ColorF3Blue")) {
            assertTrue(combo in WEB_KEY_COMBOS, "$combo is not bound")
        }
    }

    @Test
    fun theMediaKeysARemoteSendsSurvive() {
        for (combo in listOf(
            "MediaPlay", "MediaPause", "MediaPlayPause", "MediaStop",
            "MediaRewind", "MediaFastForward", "MediaTrackNext", "MediaTrackPrevious",
        )) {
            assertTrue(combo in WEB_KEY_COMBOS, "$combo is not bound")
        }
    }

    // Three keys each, because three habits exist. Keeping one looks like
    // tidying and is how a viewer's habit stops working.
    @Test
    fun subtitlesAndAudioKeepAllThreeKeysEach() {
        val subtitles: List<String> = WEB_KEY_BINDINGS
            .filter { it.action == KeyAction.CYCLE_SUBTITLES }
            .map { it.combo }
        val audio: List<String> = WEB_KEY_BINDINGS
            .filter { it.action == KeyAction.CYCLE_AUDIO }
            .map { it.combo }

        assertEquals(listOf("Subtitle", "5", "v"), subtitles)
        assertEquals(listOf("Audio", "2", "b"), audio)
    }

    // shift 3, alt 10, ctrl 60 — the VLC habit, and the numbers matter.
    @Test
    fun theModifierSeeksKeepTheirDistances() {
        val byCombo: Map<String, KeyAction> = WEB_KEY_BINDINGS.associate { it.combo to it.action }

        assertEquals(KeyAction.SEEK_BACK_3, byCombo["shift+ArrowLeft"])
        assertEquals(KeyAction.SEEK_FORWARD_3, byCombo["shift+ArrowRight"])
        assertEquals(KeyAction.SEEK_BACK_10, byCombo["alt+ArrowLeft"])
        assertEquals(KeyAction.SEEK_FORWARD_10, byCombo["alt+ArrowRight"])
        assertEquals(KeyAction.SEEK_BACK_60, byCombo["ctrl+ArrowLeft"])
        assertEquals(KeyAction.SEEK_FORWARD_60, byCombo["ctrl+ArrowRight"])
    }

    // Two ways to reach fullscreen, and Escape leaves rather than toggles.
    @Test
    fun fullscreenHasBothKeysAndEscapeOnlyExits() {
        val byCombo: Map<String, KeyAction> = WEB_KEY_BINDINGS.associate { it.combo to it.action }

        assertEquals(KeyAction.TOGGLE_FULLSCREEN, byCombo["f"])
        assertEquals(KeyAction.TOGGLE_FULLSCREEN, byCombo["F11"])
        assertEquals(KeyAction.EXIT_FULLSCREEN, byCombo["Escape"])
    }

    // The four keys that read as something else.
    //
    // Every one of these had a plausible wrong answer in this table and none of
    // them had a case here, which is how the table shipped claiming `[` and `]`
    // were chapters, `e` was picture-in-picture, `t` was theater mode and `s`
    // opened the settings. Nothing was red: a fixture with no assertion on it
    // grades nothing, and the handler beside it had been right the whole time.
    @Test
    fun theKeysThatLookLikeSomethingElseAreWhatTheWebBinds() {
        val byCombo: Map<String, KeyAction> = WEB_KEY_BINDINGS.associate { it.combo to it.action }

        // Bracket keys are markers in half the world's software. Here they are speed.
        assertEquals(KeyAction.SPEED_UP, byCombo["]"])
        assertEquals(KeyAction.SPEED_DOWN, byCombo["["])
        // Plus and minus are the size of the SUBTITLES, which is what is left
        // once the brackets have taken the speed.
        assertEquals(KeyAction.SUBTITLE_SIZE_UP, byCombo["+"])
        assertEquals(KeyAction.SUBTITLE_SIZE_UP, byCombo["shift++"])
        assertEquals(KeyAction.SUBTITLE_SIZE_DOWN, byCombo["-"])
        // One frame, not picture-in-picture.
        assertEquals(KeyAction.FRAME_ADVANCE, byCombo["e"])
        // The clock, not theater mode — the web player has no theater key.
        assertEquals(KeyAction.SHOW_TIME, byCombo["t"])
        // Stop. Not the settings menu.
        assertEquals(KeyAction.STOP, byCombo["s"])
    }

    // The four jumps, and the numbers are not in the order the digits are.
    @Test
    fun theQuickSkipsJumpForwardAndTheColoursMatchThem() {
        val byCombo: Map<String, KeyAction> = WEB_KEY_BINDINGS.associate { it.combo to it.action }

        assertEquals(KeyAction.SEEK_FORWARD_120, byCombo["1"])
        assertEquals(KeyAction.SEEK_FORWARD_30, byCombo["3"])
        assertEquals(KeyAction.SEEK_FORWARD_60, byCombo["6"])
        assertEquals(KeyAction.SEEK_FORWARD_90, byCombo["9"])

        // The remote's four are the same four, so nobody learns two sets.
        assertEquals(byCombo["3"], byCombo["ColorF0Red"])
        assertEquals(byCombo["6"], byCombo["ColorF1Green"])
        assertEquals(byCombo["9"], byCombo["ColorF2Yellow"])
        assertEquals(byCombo["1"], byCombo["ColorF3Blue"])
    }

    // Five, and it is the number the shortcuts panel prints beside the arrows.
    @Test
    fun aBareArrowMovesFiveSecondsAndNotTheAltDistance() {
        val byCombo: Map<String, KeyAction> = WEB_KEY_BINDINGS.associate { it.combo to it.action }

        assertEquals(KeyAction.SEEK_BACK_5, byCombo["ArrowLeft"])
        assertEquals(KeyAction.SEEK_FORWARD_5, byCombo["ArrowRight"])
        assertEquals(KeyAction.SEEK_BACK_5, byCombo["MediaRewind"])
        assertEquals(KeyAction.SEEK_FORWARD_5, byCombo["MediaFastForward"])
        // Ten belongs to alt, and having one constant serve both is how the
        // bare arrow came to move twice as far as the panel says it does.
        assertEquals(KeyAction.SEEK_FORWARD_10, byCombo["alt+ArrowRight"])
    }

    // Both aspect-ratio keys, and neither of them opens anything.
    @Test
    fun theAspectRatioKeysAreALetterAndARemoteButton() {
        val cycles: List<String> = WEB_KEY_BINDINGS
            .filter { it.action == KeyAction.CYCLE_ASPECT_RATIO }
            .map { it.combo }

        assertEquals(listOf("a", "BrowserFavorites"), cycles)
    }

    // Nothing in this table may name a feature the web player does not have.
    //
    // The wrong version invented three: theater mode, picture-in-picture and a
    // settings toggle, none of which the key handler binds anywhere. An action
    // no combo uses is the shape that mistake takes, so it is refused outright.
    @Test
    fun everyActionIsOneSomeKeyActuallyPerforms() {
        val used: Set<KeyAction> = WEB_KEY_BINDINGS.map { it.action }.toSet()
        val unused: List<KeyAction> = KeyAction.entries.filterNot { it in used }

        assertTrue(unused.isEmpty(), "no key performs: $unused")
    }

    private companion object {
        const val EXPECTED_BINDINGS = 53
    }
}
