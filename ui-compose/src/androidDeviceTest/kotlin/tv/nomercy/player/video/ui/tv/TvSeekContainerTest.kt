// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Scrubbing with a remote, on a remote.
//
// The rule under all of it is that the film does not move until the viewer
// commits. A scrubber that seeked on every press would make hunting for a scene
// a series of jumps through a film that is also playing.
class TvSeekContainerTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recording : TvChromeCallbacks {
        val overrides: MutableList<Float?> = mutableListOf()
        val seeks: MutableList<Float> = mutableListOf()
        override fun play() = Unit
        override fun pause() = Unit
        override fun togglePlay() = Unit
        override fun seek(seconds: Float) { seeks += seconds }
        override fun overrideTime(seconds: Float?) { overrides += seconds }
        override fun restart() = Unit
        override fun next() = Unit
        override fun exitPlayer() = Unit
    }

    private val callbacks = Recording()
    private var committed: Float? = null
    private var cancelled = 0

    private fun render(atSeconds: Double = START) {
        compose.setContent {
            TvSeekContainer(
                state = TvTransportState(isPlaying = false, timeSeconds = atSeconds, durationSeconds = WHOLE_FILM),
                callbacks = callbacks,
                onCommit = { committed = it },
                onCancel = { cancelled += 1 },
            )
        }
    }

    private fun press(key: Key) {
        compose.onNodeWithTag(SEEK_TAG).performKeyInput { pressKey(key) }
    }

    private fun shown(): String =
        compose.onNodeWithTag(SEEK_TAG)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .first()

    @Test
    fun aSidewaysPressMovesThePreviewRatherThanTheFilm() {
        // The whole design. Seeking on every press turns hunting for a scene
        // into a series of jumps through a film that is also playing.
        render()

        press(Key.DirectionRight)

        assertEquals("1:10", shown())
        assertEquals(emptyList(), callbacks.seeks)
    }

    @Test
    fun itMovesBothWays() {
        render()

        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionLeft)

        assertEquals("1:10", shown())
    }

    @Test
    fun theDisplayFollowsThePreviewSoTheViewerSeesWhereTheyAre() {
        // The bar behind this is showing the real position. Without the override
        // a viewer scrubbing sees a number that does not move.
        render()

        press(Key.DirectionRight)

        assertTrue(callbacks.overrides.isNotEmpty())
        assertEquals(ONE_STEP_ON, callbacks.overrides.last())
    }

    @Test
    fun itWillNotScrubPastTheStartOfTheFilm() {
        render(atSeconds = NEAR_THE_START)

        press(Key.DirectionLeft)
        press(Key.DirectionLeft)

        assertEquals("0:00", shown())
    }

    @Test
    fun itWillNotScrubPastTheEnd() {
        // A preview beyond the film is a position nothing can seek to, and the
        // commit that follows it lands wherever the engine decides.
        render(atSeconds = WHOLE_FILM - NEAR_THE_START)

        press(Key.DirectionRight)
        press(Key.DirectionRight)

        assertEquals(formatTime(WHOLE_FILM), shown())
    }

    @Test
    fun committingMovesTheFilmToWhereTheViewerLooked() {
        render()

        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertEquals(ONE_STEP_ON, committed)
    }

    @Test
    fun backLeavesWithoutMovingAnything() {
        render()
        press(Key.DirectionRight)

        press(Key.Back)

        assertEquals(1, cancelled)
        assertEquals(null, committed)
        assertEquals(emptyList(), callbacks.seeks)
    }
}

private const val START = 60.0
private const val WHOLE_FILM = 600.0
private const val ONE_STEP_ON = 70f
private const val NEAR_THE_START = 5.0
