// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitlePayload
import tv.nomercy.player.core.events.SubtitlesPayload
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A teardown that is already running must not overwrite a newer selection.
 *
 * Turning captions off and straight back on is two selections a few hundred
 * milliseconds apart, and the teardown for the first one recorded "nothing is
 * loaded" when it FINISHED rather than when it was decided. Land that write
 * after the reselection and the plugin holds no url, the reconcile that would
 * have loaded the styled track sees a target that no longer matches, and the
 * screen stays dark until the queue moves on — which is the exact symptom
 * SubtitleFollowsTheSelectionTest was written for, arriving through the one
 * door it did not cover.
 *
 * It reached CI as a test that failed roughly one run in five, on a different
 * assertion each time, which is what a race looks like from the outside. This
 * pins the interleaving instead of hoping for it: the renderer re-enters the
 * player at the exact moment the teardown is inside libass, so the newer
 * selection is guaranteed to land mid-flight on every host and every run.
 */
class SelectionOutlivesAnInFlightTeardownTest {

    @Test
    fun aReselectionDuringTheTeardownIsTheOneThatStands() = runTest {
        val player = ComposedPlayer(backend = null).also { it.setup(PlayerConfig()) }

        // Fires when the plugin tells the renderer to draw nothing, which is the
        // middle of turning captions off.
        val duringTeardown = ReentrantRenderer(onEmptyTrack = { select(player, index = 1) })
        val plugin = SubtitlePlugin(duringTeardown).also { it.dispatcher = StandardTestDispatcher(testScheduler) }
        player.addPlugin(plugin)

        player.emit(CoreEvents.Subtitles, SubtitlesPayload(tracks = TRACKS))

        select(player, index = 1)
        select(player, index = null)

        // The launched work, run to completion on this test's own scheduler.
        // Asserting before this is asserting on whether a coroutine happened to
        // have started, which is the flake this test replaces.
        advanceUntilIdle()

        assertEquals(
            ASS_URL,
            plugin.subtitle(),
            "the teardown finished last and wrote its own answer over the newer selection",
        )
    }

    private fun select(player: ComposedPlayer, index: Int?) {
        player.emit(CoreEvents.Subtitle, SubtitlePayload(track = index?.toDouble()))
    }

    private class ReentrantRenderer(private val onEmptyTrack: () -> Unit) : AssRenderer {

        private var reentered: Boolean = false

        override fun addFont(name: String, data: ByteArray): Unit = Unit

        override fun clearFonts(): Unit = Unit

        override fun loadTrack(assContent: String) {
            // Once, and only for the teardown's empty track. Re-entering on
            // every call would recurse forever, and re-entering on a real load
            // is a different question from this one.
            if (assContent.isNotEmpty() || reentered) return
            reentered = true
            onEmptyTrack()
        }

        override fun storageSize(width: Int, height: Int): Unit = Unit

        override fun storageSize(): AssSize? = null

        override fun frameSize(width: Int, height: Int): Unit = Unit

        override fun render(timeMillis: Long): AssFrame? = null

        override fun release(): Unit = Unit
    }

    private companion object {
        const val ASS_URL = "https://media.example.test/show/1/signs.ass"

        val TRACKS: List<Any?> = listOf(
            SubtitleTrack(id = "en-vtt", language = "en", label = "English", format = "vtt", url = "https://media.example.test/show/1/en.vtt"),
            SubtitleTrack(id = "en-ass", language = "en", label = "English (Full)", format = "ass", url = ASS_URL),
        )
    }
}
