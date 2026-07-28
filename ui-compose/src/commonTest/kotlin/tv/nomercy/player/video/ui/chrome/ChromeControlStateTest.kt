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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeControlStateTest {

    private val idle = ChromeControlValues()

    // One rule across eight controls: non-default state gets the filled icon.
    @Test
    fun nothingIsActiveInTheDefaultState() {
        ChromeControl.entries.forEach { control ->
            assertFalse(isControlActive(control, idle), "$control was active at rest")
        }
    }

    @Test
    fun eachNonDefaultSettingLightsItsOwnControl() {
        assertTrue(isControlActive(ChromeControl.SPEED, idle.copy(rate = 1.5)))
        assertTrue(isControlActive(ChromeControl.QUALITY, idle.copy(manualQuality = true)))
        assertTrue(isControlActive(ChromeControl.SUBTITLES, idle.copy(subtitleTrack = 0)))
        assertTrue(isControlActive(ChromeControl.PIP, idle.copy(pictureInPicture = true)))
        assertTrue(isControlActive(ChromeControl.AUDIO, idle.copy(nonDefaultAudio = true)))
        assertTrue(isControlActive(ChromeControl.ASPECT_RATIO, idle.copy(aspect = "fill")))
        assertTrue(isControlActive(ChromeControl.MUTE, idle.copy(muted = true)))
    }

    // Track 0 is a real selection. Treating it as "none" because it is falsy is
    // the classic version of this bug, and it leaves the first subtitle track
    // looking unselected.
    @Test
    fun theFirstSubtitleTrackCountsAsSelected() {
        assertTrue(isControlActive(ChromeControl.SUBTITLES, idle.copy(subtitleTrack = 0)))
        assertFalse(isControlActive(ChromeControl.SUBTITLES, idle.copy(subtitleTrack = null)))
    }

    // Disabled at BOTH ends on a single-item queue, not neither.
    @Test
    fun aSingleItemQueueDisablesBothEnds() {
        val alone = idle.copy(index = 0, queueLength = 1)

        assertFalse(isControlEnabled(ChromeControl.PREVIOUS, alone))
        assertFalse(isControlEnabled(ChromeControl.NEXT, alone))
    }

    @Test
    fun theEndsOfAQueueDisableOneSideEach() {
        val first = idle.copy(index = 0, queueLength = 3)
        val middle = idle.copy(index = 1, queueLength = 3)
        val last = idle.copy(index = 2, queueLength = 3)

        assertFalse(isControlEnabled(ChromeControl.PREVIOUS, first))
        assertTrue(isControlEnabled(ChromeControl.NEXT, first))

        assertTrue(isControlEnabled(ChromeControl.PREVIOUS, middle))
        assertTrue(isControlEnabled(ChromeControl.NEXT, middle))

        assertTrue(isControlEnabled(ChromeControl.PREVIOUS, last))
        assertFalse(isControlEnabled(ChromeControl.NEXT, last))
    }

    @Test
    fun seekBackIsDeadAtExactlyZero() {
        assertFalse(isControlEnabled(ChromeControl.SEEK_BACK, idle.copy(positionSeconds = 0.0)))
        assertTrue(isControlEnabled(ChromeControl.SEEK_BACK, idle.copy(positionSeconds = 0.1)))
    }

    // Within a quarter second of the end, not at it. The last frames of a
    // decode never report the duration exactly, so an equality check leaves the
    // button live on an item that has finished.
    @Test
    fun seekForwardDiesAQuarterSecondBeforeTheEnd() {
        val near = idle.copy(positionSeconds = 99.8, durationSeconds = 100.0)
        val earlier = idle.copy(positionSeconds = 99.7, durationSeconds = 100.0)

        assertFalse(isControlEnabled(ChromeControl.SEEK_FORWARD, near))
        assertTrue(isControlEnabled(ChromeControl.SEEK_FORWARD, earlier))
    }

    // A duration of zero means it is not known yet, and a player that has not
    // reported one has not ended.
    @Test
    fun seekForwardStaysLiveBeforeADurationIsKnown() {
        assertTrue(
            isControlEnabled(ChromeControl.SEEK_FORWARD, idle.copy(durationSeconds = 0.0)),
        )
    }

    @Test
    fun theChapterJumpsNeedAChapterOutsideTheGrace() {
        val state = idle.copy(positionSeconds = 30.0, chapterStarts = listOf(0.0, 30.5, 60.0))

        assertTrue(isControlEnabled(ChromeControl.CHAPTER_PREV, state))
        assertTrue(isControlEnabled(ChromeControl.CHAPTER_NEXT, state))

        // 30.5 is inside the one-second grace on both sides, so it counts for
        // neither: a chapter you are already effectively at is not somewhere to
        // jump to.
        val onlyNearby = idle.copy(positionSeconds = 30.0, chapterStarts = listOf(30.5))
        assertFalse(isControlEnabled(ChromeControl.CHAPTER_PREV, onlyNearby))
        assertFalse(isControlEnabled(ChromeControl.CHAPTER_NEXT, onlyNearby))
    }

    @Test
    fun anItemWithoutChaptersDisablesBothJumps() {
        assertFalse(isControlEnabled(ChromeControl.CHAPTER_PREV, idle))
        assertFalse(isControlEnabled(ChromeControl.CHAPTER_NEXT, idle))
    }

    // Theater is meaningless inside fullscreen and inside picture-in-picture.
    @Test
    fun theaterLeavesTheBarInFullscreenAndPip() {
        assertTrue(showsTheater(idle))
        assertFalse(showsTheater(idle.copy(fullscreen = true)))
        assertFalse(showsTheater(idle.copy(pictureInPicture = true)))
    }

    // Four steps, not two, or the button stops describing the level it is set
    // to. The thresholds are the web's.
    @Test
    fun theVolumeGlyphHasFourSteps() {
        assertEquals(VolumeLevel.Muted, volumeLevel(idle.copy(volumePercent = 0)))
        assertEquals(VolumeLevel.Low, volumeLevel(idle.copy(volumePercent = 29)))
        assertEquals(VolumeLevel.Medium, volumeLevel(idle.copy(volumePercent = 30)))
        assertEquals(VolumeLevel.Medium, volumeLevel(idle.copy(volumePercent = 60)))
        assertEquals(VolumeLevel.High, volumeLevel(idle.copy(volumePercent = 61)))
    }

    // Muted wins over a non-zero level, or unmuting would be the only way to
    // find out the player was muted.
    @Test
    fun mutedBeatsTheLevel() {
        assertEquals(VolumeLevel.Muted, volumeLevel(idle.copy(muted = true, volumePercent = 100)))
    }
}
