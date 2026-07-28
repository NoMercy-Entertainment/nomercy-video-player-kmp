// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.chrome.ChromeTestItem
import tv.nomercy.player.video.ui.chrome.RecordingVideoBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tv.nomercy.player.video.tv.TvTrack

// The television chrome against a real player.
//
// Everything here goes by identifier, and that is the point: the lists are drawn
// once and the engine may report a different set a second later, so a menu that
// selected by position would switch to whatever moved into that slot. Which is
// the wrong audio language, silently, on exactly the streams that adapt most.
@OptIn(ExperimentalTestApi::class)
abstract class TvChromeBindingGate {

    private class Mounted(val player: NMVideoPlayer, val chrome: () -> TvChrome)

    private fun ComposeUiTest.mount(): Mounted {
        val player = NMVideoPlayer(RecordingVideoBackend())
        var latest: TvChrome? = null

        setContent {
            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestItem()))
                player.load(ChromeTestItem())
            }

            latest = rememberTvChrome(player)
        }
        waitForIdle()

        return Mounted(player) { requireNotNull(latest) }
    }

    @Test
    fun theTrackListsCarryWhatIsPlaying() = runComposeUiTest {
        val mounted = mount()

        val current: List<TvTrack> = mounted.chrome().content.audioTracks.filter { it.isCurrent }

        assertEquals(1, current.size)
        assertEquals(RecordingVideoBackend.TRACKS.first().id, current.single().id)
    }

    @Test
    fun choosingAnAudioTrackByNameReachesTheEngine() = runComposeUiTest {
        val mounted = mount()
        val wanted: String = RecordingVideoBackend.TRACKS.last().id

        mounted.chrome().controller.chooseAudioTrack(wanted)
        waitForIdle()

        assertEquals(wanted, mounted.player.audioTrack()?.id)
    }

    @Test
    fun anIdentifierThatMatchesNothingTurnsSubtitlesOff() = runComposeUiTest {
        // Off is a row in the list rather than an absence, so it arrives here as
        // an identifier like any other. A binding that ignored the miss would
        // leave a viewer unable to turn them off at all.
        val mounted = mount()
        mounted.chrome().controller.chooseSubtitleTrack(RecordingVideoBackend.SUBTITLES.first().id)
        waitForIdle()

        mounted.chrome().controller.chooseSubtitleTrack("none")
        waitForIdle()

        assertNull(mounted.player.subtitle())
    }

    @Test
    fun theBarShowsThePreviewWhileAScrubIsInProgress() = runComposeUiTest {
        // The film has not moved. Seeking on every step of a scrub is the seek
        // storm the engine answers by stalling, and the picture a viewer is
        // hunting for never settles long enough to be recognised.
        val mounted = mount()

        mounted.chrome().controller.callbacks.overrideTime(PREVIEW_SECONDS)
        waitForIdle()

        assertEquals(PREVIEW_SECONDS.toDouble(), mounted.chrome().transport.timeSeconds)
        assertTrue(mounted.player.time() < PREVIEW_SECONDS)
    }

    @Test
    fun andStopsShowingItOnceTheScrubIsCommitted() = runComposeUiTest {
        // Committed somewhere other than where the preview was left, because
        // that is the only version of this that can fail. Asserting the bar
        // equals the player's own time passes either way when the seek lands on
        // the previewed position — which is what a first draft of this did.
        val mounted = mount()
        mounted.chrome().controller.callbacks.overrideTime(PREVIEW_SECONDS)
        waitForIdle()

        mounted.chrome().controller.callbacks.seek(COMMITTED_SECONDS)
        waitForIdle()

        assertNotEquals(PREVIEW_SECONDS.toDouble(), mounted.chrome().transport.timeSeconds)
    }
}

private const val PREVIEW_SECONDS = 42f
private const val COMMITTED_SECONDS = 90f
