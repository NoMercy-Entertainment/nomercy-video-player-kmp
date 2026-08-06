// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals

// The order controls leave the bar as it narrows.
//
// ChromeControl's declaration order IS the priority list — the bar is filled
// from the top until it runs out of room — so a member in the wrong place is a
// control that disappears at the wrong width. Nothing checked it, and two were
// swapped: the file claimed to be DEFAULT_PRIORITY "in order" and was not.
//
// Transcribed from packages/nomercy-video-player/src/plugins/desktop-ui/
// helpers/responsive.ts. If that list moves, this fails and says which rank.
class ChromePriorityOrderTest {

    @Test
    fun theDropOrderIsTheReferencePriorityList() {
        assertEquals(REFERENCE_ORDER, ChromeControl.entries.map { it.name })
    }

    // Forward above back is the one a reader will want to change back, so it
    // gets its own test and the reason with it: ranking back higher makes the
    // bar drop forward first and leave a lone rewind button on it, which reads
    // as a player that can only go backwards.
    @Test
    fun seekForwardOutranksSeekBack() {
        val order: List<ChromeControl> = ChromeControl.entries
        assertEquals(
            true,
            order.indexOf(ChromeControl.SEEK_FORWARD) < order.indexOf(ChromeControl.SEEK_BACK),
        )
    }

    private companion object {
        val REFERENCE_ORDER: List<String> = listOf(
            "PLAY", "MUTE", "VOLUME", "FULLSCREEN", "SETTINGS", "NEXT", "PREVIOUS",
            "CHAPTER_PREV", "CHAPTER_NEXT", "SEEK_FORWARD", "SEEK_BACK", "THEATER",
            "PIP", "SPEED", "QUALITY", "SUBTITLES", "AUDIO", "ASPECT_RATIO", "PLAYLIST",
        )
    }
}
