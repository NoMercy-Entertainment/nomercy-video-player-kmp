// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals

// What a language row is called.
//
// The web asks four questions in order:
//
//     audioTrack.label ?? languageDisplayName(...) ?? audioTrack.language
//       ?? `Track ${i + 1}`
//
// The port asked the first and stopped, and the desktop backend had already
// collapsed the rest: an unlabelled track's label defaults to the language CODE,
// and a track with no language at all to the literal "und". So a viewer chose
// between two rows both called "und" where the browser offers a spelled-out
// language and a numbered track.
class TrackLabelTest {

    private fun audio(label: String, language: String): AudioTrack =
        AudioTrack(id = "1", language = language, label = label, channels = 2, codec = "aac")

    @Test
    fun aTrackThatNamesItselfKeepsItsName() {
        assertEquals("Director's commentary", audioLabel(audio("Director's commentary", "en"), 0))
    }

    @Test
    fun aTrackNamedOnlyByItsCodeIsSpelledOut() {
        // The row the desktop drew as "nl". A viewer reads a language, not a tag.
        assertEquals("Nederlands", audioLabel(audio("nl", "nl"), 0))
    }

    @Test
    fun andATrackWithNoLanguageAtAllIsNumbered() {
        // "und" is libVLC's answer for a track that declares nothing, and it
        // reached the menu verbatim - twice over on a file with two such tracks,
        // which is a list nobody can choose from.
        assertEquals("Track 2", audioLabel(audio("und", "und"), 1))
    }

    @Test
    fun aSubtitleRowIsNamedByTheSameRule() {
        // The two panes had drifted: the audio pane read its label through a
        // helper and the subtitle pane read the field directly.
        val track = SubtitleTrack(id = "3", language = "und", label = "und", format = "subrip")

        assertEquals("Track 1", subtitleLabel(track, 0))
    }
}
