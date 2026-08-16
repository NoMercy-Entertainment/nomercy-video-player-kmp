// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.ports.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals

// What a track change says on screen.
//
// The message channel carried volume and mute and neither track event, so
// selecting a subtitle showed nothing at all — and a styled one is fetched,
// then its fonts, then rasterised, so the silence lasted until the first cue.
// Reported on a real phone: "no indication of the subtitles loading".
class ChromeTrackMessageTest {

    private val tracks = listOf(
        SubtitleTrack(id = "0", language = "eng", label = "English (Full)"),
        SubtitleTrack(id = "1", language = "eng", label = "English (Signs)"),
    )

    @Test
    fun theChosenTrackIsNamed() {
        assertEquals("Subtitles: English (Full)", trackMessage("Subtitles", tracks, 0.0, "Off") { it.label })
        assertEquals("Subtitles: English (Signs)", trackMessage("Subtitles", tracks, 1.0, "Off") { it.label })
    }

    // Turning captions off is the null index, and it has to read as Off rather
    // than as the previous track or an empty colon.
    @Test
    fun noTrackReadsAsOff() {
        assertEquals("Subtitles: Off", trackMessage("Subtitles", tracks, null, "Off") { it.label })
    }

    // An index past the end is the same answer as none, not a crash.
    @Test
    fun anIndexPastTheEndIsAlsoOff() {
        assertEquals("Subtitles: Off", trackMessage("Subtitles", tracks, 9.0, "Off") { it.label })
    }

    @Test
    fun theHostsWordsAreUsedForBothHalves() {
        assertEquals("Ondertitels: Uit", trackMessage("Ondertitels", tracks, null, "Uit") { it.label })
    }
}
