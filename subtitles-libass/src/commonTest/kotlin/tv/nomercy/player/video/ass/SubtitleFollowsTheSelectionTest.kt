// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitlePayload
import tv.nomercy.player.core.events.SubtitlesPayload
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The overlay follows the SELECTION, in both directions.
 *
 * Turning captions off and turning them back on are one mechanism, and only the
 * teardown half was wired: the plugin was handed a file when the ITEM changed,
 * so switching back to a styled track after switching away from it did nothing
 * at all and stayed dark until the queue moved on. Stoney found it immediately —
 * "after turning it off it will not turn back on again and requires a playlist
 * item switch before it functions again."
 *
 * Which means the plugin has to resolve the selection itself rather than wait to
 * be handed a url. It caches the track list from `subtitles` and reads the index
 * off `subtitle`, both of which core already emits.
 */
class SubtitleFollowsTheSelectionTest {

    @Test
    fun choosingTheStyledTrackAgainBringsItBack() = runTest {
        val player = play()
        val plugin = SubtitlePlugin(RecordingRenderer())
        player.addPlugin(plugin)
        announceTracks(player)

        select(player, index = 1)
        select(player, index = null)
        select(player, index = 1)

        assertEquals(ASS_URL, plugin.subtitle())
    }

    @Test
    fun choosingTheWebvttTrackTakesTheStyledOneDown() = runTest {
        val player = play()
        val plugin = SubtitlePlugin(RecordingRenderer())
        player.addPlugin(plugin)
        announceTracks(player)

        select(player, index = 1)
        select(player, index = 0)

        assertNull(plugin.subtitle(), "a WebVTT selection left the styled track drawn")
    }

    @Test
    fun turningCaptionsOffTakesItDown() = runTest {
        val player = play()
        val plugin = SubtitlePlugin(RecordingRenderer())
        player.addPlugin(plugin)
        announceTracks(player)

        select(player, index = 1)
        select(player, index = null)

        assertNull(plugin.subtitle())
    }

    private suspend fun play(): ComposedPlayer =
        ComposedPlayer(backend = null).also { it.setup(PlayerConfig()) }

    private fun announceTracks(player: ComposedPlayer) {
        player.emit(CoreEvents.Subtitles, SubtitlesPayload(tracks = TRACKS))
    }

    private fun select(player: ComposedPlayer, index: Int?) {
        player.emit(CoreEvents.Subtitle, SubtitlePayload(track = index?.toDouble()))
    }

    private companion object {
        const val ASS_URL = "https://media.example.test/show/1/signs.ass"

        val TRACKS: List<Any?> = listOf(
            SubtitleTrack(id = "en-vtt", language = "en", label = "English", format = "vtt", url = "https://media.example.test/show/1/en.vtt"),
            SubtitleTrack(id = "en-ass", language = "en", label = "English (Full)", format = "ass", url = ASS_URL),
        )
    }
}
