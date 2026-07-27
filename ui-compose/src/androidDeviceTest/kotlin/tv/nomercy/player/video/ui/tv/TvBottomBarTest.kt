// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

// The transport row, rendered on a television.
//
// On a device rather than in a shim, because the two things worth checking are
// both about what actually reached the screen: which glyph is on the button, and
// where the bar has filled to.
class TvBottomBarTest {

    @get:Rule
    val compose = createComposeRule()

    private class NoopCallbacks : TvChromeCallbacks {
        var toggles: Int = 0
        override fun play() = Unit
        override fun pause() = Unit
        override fun togglePlay() { toggles += 1 }
        override fun seek(seconds: Float) = Unit
        override fun overrideTime(seconds: Float?) = Unit
        override fun restart() = Unit
        override fun next() = Unit
        override fun exitPlayer() = Unit
    }

    private val strings = TvChromeStrings()

    private fun render(
        isPlaying: Boolean,
        timeSeconds: Double = 0.0,
        durationSeconds: Double = WHOLE_FILM,
        chapters: List<TvChapter> = emptyList(),
    ) {
        compose.setContent {
            TvBottomBar(
                state = TvTransportState(isPlaying, timeSeconds, durationSeconds, chapters),
                callbacks = NoopCallbacks(),
                strings = strings,
            )
        }
    }

    private fun reportedProgress(): Float =
        compose.onNodeWithTag(PROGRESS_TAG)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
            .current

    @Test
    fun aPlayingFilmOffersPauseRatherThanPlay() {
        // The single most-pressed control on a television. Showing the wrong
        // glyph makes a viewer press it to find out what it does.
        render(isPlaying = true)

        compose.onNodeWithContentDescription(strings.pause).assertIsDisplayed()
    }

    @Test
    fun aPausedFilmOffersPlay() {
        render(isPlaying = false)

        compose.onNodeWithContentDescription(strings.play).assertIsDisplayed()
    }

    @Test
    fun theBarReportsHowFarThroughTheFilmIs() {
        render(isPlaying = true, timeSeconds = THIRTY_SECONDS)

        assertEquals(THIRTY_OF_A_HUNDRED, reportedProgress())
    }

    @Test
    fun aStreamWithNoKnownLengthReportsNothingRatherThanDividingByZero() {
        // A live stream. A bar that filled from an unknown duration would show
        // whatever the arithmetic produced, which is usually the whole width.
        render(isPlaying = true, timeSeconds = THIRTY_SECONDS, durationSeconds = 0.0)

        assertEquals(0f, reportedProgress())
    }

    @Test
    fun aPositionPastTheEndDoesNotOverflowTheBar() {
        // Ordinary at the end of a file: the engine reports a position slightly
        // past a duration it has not refreshed. A bar drawn wider than itself
        // looks broken.
        render(isPlaying = true, timeSeconds = PAST_THE_END)

        assertEquals(1f, reportedProgress())
    }

    @Test
    fun everyButtonIsReachableByNameSoARemoteAndAReaderBothFindIt() {
        // Unlabelled icon buttons are invisible to a screen reader and to a
        // test, and on a television there is no tooltip to fall back on.
        render(isPlaying = true)

        compose.onNodeWithContentDescription(strings.restart).assertIsDisplayed()
        compose.onNodeWithContentDescription(strings.pause).assertIsDisplayed()
        compose.onNodeWithContentDescription(strings.next).assertIsDisplayed()
    }

    @Test
    fun theRowIsThereWithoutAStoreBehindIt() {
        // The whole extraction, in one assertion: it renders from state and
        // callbacks, with nothing that could reach a playback store.
        render(isPlaying = true, chapters = listOf(TvChapter(THIRTY_SECONDS), TvChapter(SIXTY_SECONDS)))

        compose.onNodeWithTag(BOTTOM_BAR_TAG).assertIsDisplayed()
    }
}

private const val WHOLE_FILM = 100.0
private const val THIRTY_SECONDS = 30.0
private const val SIXTY_SECONDS = 60.0
private const val PAST_THE_END = 120.0
private const val THIRTY_OF_A_HUNDRED = 0.3f
