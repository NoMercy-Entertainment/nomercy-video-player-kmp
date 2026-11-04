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
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.events.SubtitlePayload
import tv.nomercy.player.core.events.SubtitlesPayload
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.FetchResponse
import tv.nomercy.player.core.ports.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * On a phone the next item's `subtitle` event can land before its `item` event
 * (they are emitted from different threads). `item` then wipes the selection
 * the plugin just took, and nothing announces it again; only the track the
 * engine carries is left to adopt, on `mediaReady`.
 */
class SubtitleAnnouncedBeforeItemTest {

    @Test
    fun theTrackAnnouncedBeforeTheItemEventIsStillDrawn() = runTest {
        var carried: SubtitleTrack? = null
        val player: ComposedPlayer =
            object : ComposedPlayer(
                backend = null,
                fetcher = { _, _ -> FetchResponse(status = 200, body = SCRIPT) },
            ) {
                override fun subtitle(): SubtitleTrack? = carried
            }
        val plugin = SubtitlePlugin(RecordingRenderer()).also { it.dispatcher = StandardTestDispatcher(testScheduler) }
        player.setup(PlayerConfig())
        player.addPlugin(plugin)
        player.emit(CoreEvents.Subtitles, SubtitlesPayload(tracks = TRACKS))
        player.emit(CoreEvents.Subtitle, SubtitlePayload(track = 0.0))
        advanceUntilIdle()
        assertEquals(ASS_URL, plugin.subtitle())

        carried = TRACKS[0]
        player.emit(CoreEvents.Subtitles, SubtitlesPayload(tracks = NEXT_TRACKS))
        player.emit(CoreEvents.Subtitle, SubtitlePayload(track = 0.0))
        player.emit(CoreEvents.Item, ItemChange(item = null, index = 1))
        player.emit(CoreEvents.MediaReady, Unit)
        advanceUntilIdle()

        assertEquals(NEXT_ASS_URL, plugin.subtitle(), "the next item stayed dark after item wiped the announced selection")
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
