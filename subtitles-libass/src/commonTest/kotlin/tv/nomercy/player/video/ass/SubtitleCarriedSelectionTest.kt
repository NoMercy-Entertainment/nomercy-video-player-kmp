// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertNull

/**
 * A styled selection made on one item is drawn on the next without being
 * announced again.
 *
 * The engine keeps the viewer's track across items and emits no `subtitle` for
 * the new one, while this plugin forgets its selection on `item`. The next
 * episode then showed no captions until the viewer picked the track again; the
 * web plugin had the same hole and re-applies the carried selection on
 * `subtitles` and `mediaReady`.
 */
class SubtitleCarriedSelectionTest {

    @Test
    fun theStyledTrackTheEngineCarriesIsDrawnOnTheNextItem() = runTest {
        val rig = rig()
        rig.selectByHand(index = 1)
        rig.advanceUntilIdle()
        assertEquals(ASS_URL, rig.plugin.subtitle())

        rig.moveToNextItem(engineTrack = TRACKS[1])
        rig.advanceUntilIdle()

        assertEquals(NEXT_ASS_URL, rig.plugin.subtitle(), "the carried styled track was not drawn on the next item")
    }

    @Test
    fun nothingIsDrawnWhenTheEngineCarriesNoTrack() = runTest {
        val rig = rig()
        rig.selectByHand(index = 1)
        rig.advanceUntilIdle()

        rig.moveToNextItem(engineTrack = null)
        rig.advanceUntilIdle()

        assertNull(rig.plugin.subtitle())
    }

    @Test
    fun aCarriedWebvttTrackDrawsNothingStyled() = runTest {
        val rig = rig()
        rig.selectByHand(index = 0)

        rig.moveToNextItem(engineTrack = TRACKS[0])
        rig.advanceUntilIdle()

        assertNull(rig.plugin.subtitle())
    }

    @Test
    fun aViewerChoiceOnTheNextItemBeatsTheCarriedOne() = runTest {
        val rig = rig()
        rig.selectByHand(index = 1)
        rig.moveToNextItem(engineTrack = TRACKS[1])
        rig.advanceUntilIdle()

        rig.selectByHand(index = null)
        rig.mediaReady()
        rig.advanceUntilIdle()

        assertNull(rig.plugin.subtitle(), "mediaReady put back captions the viewer had just turned off")
    }

    private suspend fun TestScope.rig(): Rig {
        val rig = Rig(this)
        rig.player.setup(PlayerConfig())
        rig.start()
        return rig
    }

    private class Rig(private val scope: TestScope) {
        var engineTrack: SubtitleTrack? = null

        val player: ComposedPlayer =
            object : ComposedPlayer(
                backend = null,
                fetcher = { _, _ -> FetchResponse(status = 200, body = SCRIPT) },
            ) {
                override fun subtitle(): SubtitleTrack? = engineTrack
            }

        val plugin: SubtitlePlugin =
            SubtitlePlugin(RecordingRenderer()).also { it.dispatcher = StandardTestDispatcher(scope.testScheduler) }

        suspend fun start() {
            player.addPlugin(plugin)
            player.emit(CoreEvents.Subtitles, SubtitlesPayload(tracks = TRACKS))
        }

        fun selectByHand(index: Int?) {
            player.emit(CoreEvents.Subtitle, SubtitlePayload(track = index?.toDouble()))
        }

        // The order the video player produces: the new item's tracks are published
        // from inside the item dispatch, then the engine reports ready.
        fun moveToNextItem(engineTrack: SubtitleTrack?) {
            this.engineTrack = engineTrack
            player.emit(CoreEvents.Subtitles, SubtitlesPayload(tracks = NEXT_TRACKS))
            player.emit(CoreEvents.Item, ItemChange(item = null, index = 1))
            mediaReady()
        }

        fun mediaReady() {
            player.emit(CoreEvents.MediaReady, Unit)
        }

        suspend fun advanceUntilIdle() {
            scope.advanceUntilIdle()
        }
    }

    private companion object {
        const val SCRIPT = "[Script Info]\n[Events]\nDialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,line"
        const val ASS_URL = "https://media.example.test/show/1/full.ass"
        const val NEXT_ASS_URL = "https://media.example.test/show/2/full.ass"

        val TRACKS: List<SubtitleTrack> = listOf(
            SubtitleTrack(id = "en-vtt", language = "en", label = "English", format = "vtt", url = "https://media.example.test/show/1/en.vtt"),
            SubtitleTrack(id = "en-ass", language = "en", label = "English (Full)", format = "ass", url = ASS_URL),
        )

        val NEXT_TRACKS: List<SubtitleTrack> = listOf(
            SubtitleTrack(id = "en-vtt", language = "en", label = "English", format = "vtt", url = "https://media.example.test/show/2/en.vtt"),
            SubtitleTrack(id = "en-ass", language = "en", label = "English (Full)", format = "ass", url = NEXT_ASS_URL),
        )
    }
}
