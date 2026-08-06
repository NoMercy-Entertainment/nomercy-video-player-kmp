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

/**
 * Every control the bar can draw carries a test tag.
 *
 * An untagged control cannot be addressed by a UI test or found in a screenshot
 * comparison, so it is a control nobody can prove is on screen — and four of
 * them were in exactly that state (previous, next, subtitles, audio) while the
 * chrome scored full marks on membership. Membership counts declarations; this
 * counts what a measurement can reach.
 *
 * A list rather than a reflective sweep, because the enum is the population and
 * a new entry should FAIL here until somebody decides what it is called. That
 * decision is the value: the tag is the name the parity report keys the web's
 * element id against.
 */
class ControlTagCoverageTest {

    @Test
    fun everyDrawableControlHasATag() {
        val untagged: List<ChromeControl> = ChromeControl.entries.filter { TAGS[it] == null }

        assertEquals(
            emptyList(),
            untagged,
            "controls with no test tag cannot be measured on screen",
        )
    }

    @Test
    fun noTwoControlsShareATag() {
        val duplicated: List<String> = TAGS.values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .toList()

        assertEquals(emptyList(), duplicated, "a shared tag makes two controls indistinguishable")
    }

    private companion object {
        val TAGS: Map<ChromeControl, String> = mapOf(
            ChromeControl.PLAY to PLAY_PAUSE_TAG,
            // MUTE is the speaker button; VOLUME is the slider that grows out
            // of it on hover. The web draws the same two — `volume` inside
            // `volume-container` — and giving them one tag would make a test
            // for the slider pass on the button.
            ChromeControl.MUTE to VOLUME_TAG,
            ChromeControl.VOLUME to VOLUME_TRACK_TAG,
            ChromeControl.FULLSCREEN to FULLSCREEN_TAG,
            ChromeControl.SETTINGS to SETTINGS_TAG,
            ChromeControl.NEXT to NEXT_TAG,
            ChromeControl.PREVIOUS to PREVIOUS_TAG,
            ChromeControl.CHAPTER_PREV to CHAPTER_BACK_TAG,
            ChromeControl.CHAPTER_NEXT to CHAPTER_FORWARD_TAG,
            ChromeControl.SEEK_FORWARD to SEEK_FORWARD_TAG,
            ChromeControl.SEEK_BACK to SEEK_BACK_TAG,
            ChromeControl.THEATER to THEATER_TAG,
            ChromeControl.PIP to PIP_TAG,
            ChromeControl.SPEED to SPEED_TAG,
            ChromeControl.QUALITY to QUALITY_TAG,
            ChromeControl.SUBTITLES to SUBTITLES_TAG,
            ChromeControl.AUDIO to AUDIO_TAG,
            ChromeControl.ASPECT_RATIO to ASPECT_RATIO_TAG,
            ChromeControl.PLAYLIST to PLAYLIST_TAG,
        )
    }
}
