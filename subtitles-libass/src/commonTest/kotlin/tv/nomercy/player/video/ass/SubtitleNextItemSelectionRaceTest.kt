// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.events.SubtitlePayload
import tv.nomercy.player.core.events.SubtitlesPayload
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.FetchResponse
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real stack publishes the new item's list, then the item, then the carried
 * choice, and the first load starts the moment the list arrives. The newest
 * selection must still end up drawn once the slow first download returns.
 */
class SubtitleNextItemSelectionRaceTest {

    @Test
    fun theCarriedStyledTrackIsDrawnWhenTheListArrivesBeforeTheItem() = runTest {
        val gate = CompletableDeferred<Unit>()
        val renderer = HoldingRenderer()
        val player: ComposedPlayer = ComposedPlayer(
            backend = null,
            fetcher = { url, _ ->
                if (url == NEXT_ASS_URL) gate.await()
                FetchResponse(status = 200, body = SCRIPT)
            },
        )
        val plugin = SubtitlePlugin(renderer).also { it.dispatcher = UnconfinedTestDispatcher(testScheduler) }
        player.setup(PlayerConfig())
        player.addPlugin(plugin)
        player.emit(CoreEvents.Subtitles, SubtitlesPayload(tracks = TRACKS))
        player.emit(CoreEvents.Subtitle, SubtitlePayload(track = 0.0))
        advanceUntilIdle()
        assertTrue(renderer.holdsTrack, "the first item's track was not loaded")

        player.emit(CoreEvents.Subtitles, SubtitlesPayload(tracks = NEXT_TRACKS))
        player.emit(CoreEvents.Item, ItemChange(item = null, index = 1))
        player.emit(CoreEvents.Subtitle, SubtitlePayload(track = 0.0))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(NEXT_ASS_URL, plugin.subtitle())
        assertTrue(renderer.holdsTrack, "the next item's styled track was never installed in the renderer")
    }

    private class HoldingRenderer : AssRenderer {
        var holdsTrack: Boolean = false
        override fun addFont(name: String, data: ByteArray): Unit = Unit
        override fun clearFonts(): Unit = Unit
        override fun loadTrack(assContent: String) {
            holdsTrack = assContent.isNotEmpty()
        }
        override fun hasTrack(): Boolean = holdsTrack
        override fun storageSize(width: Int, height: Int): Unit = Unit
        override fun storageSize(): AssSize? = null
        override fun frameSize(width: Int, height: Int): Unit = Unit
        override fun render(timeMillis: Long): AssFrame? = null
        override fun release(): Unit = Unit
    }

    private companion object {
        const val SCRIPT = "[Script Info]\n[Events]\nDialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,line"
        const val ASS_URL = "https://media.example.test/show/1/full.ass"
        const val NEXT_ASS_URL = "https://media.example.test/show/2/full.ass"

        val TRACKS: List<SubtitleTrack> = listOf(
            SubtitleTrack(id = "en-ass", language = "en", label = "English (Full)", format = "ass", url = ASS_URL),
        )
        val NEXT_TRACKS: List<SubtitleTrack> = listOf(
            SubtitleTrack(id = "en-ass", language = "en", label = "English (Full)", format = "ass", url = NEXT_ASS_URL),
        )
    }
}
