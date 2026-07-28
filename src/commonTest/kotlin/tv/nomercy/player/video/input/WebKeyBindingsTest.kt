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

    private companion object {
        const val EXPECTED_BINDINGS = 53
    }
}
