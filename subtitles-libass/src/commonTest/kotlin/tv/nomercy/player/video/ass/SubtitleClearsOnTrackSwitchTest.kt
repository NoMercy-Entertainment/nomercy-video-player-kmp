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
import tv.nomercy.player.core.player.PlayerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Turning captions off, or choosing a track this renderer does not draw, takes
 * the picture down.
 *
 * The overlay's lifecycle was bound to the ITEM and not to the SELECTED TRACK:
 * the plugin loaded a track when the item changed and heard nothing at all when
 * the viewer changed tracks inside it. So ASS to ASS worked, and ASS to off and
 * ASS to VTT left the last rasterised frame on screen with libass still holding
 * the old track — a sign or a karaoke line frozen over a film that is no longer
 * showing it.
 *
 * libass has no concept of "some other renderer is drawing now". Only the
 * selection event says so, which is why this listens to the selection rather
 * than to the queue.
 */
class SubtitleClearsOnTrackSwitchTest {

    @Test
    fun turningCaptionsOffTakesTheTrackDown() = runTest {
        val renderer = RecordingRenderer()
        val plugin = installed(renderer)

        plugin.load(ASS, null)
        emitSelection(plugin, track = null)

        // The synchronous half. The native teardown is launched and its proof
        // is a photograph of the running player, not a call count a test
        // scheduler cannot wait for.
        assertNull(plugin.subtitle(), "captions were turned off and the track is still loaded")
    }

    @Test
    fun choosingATrackThisRendererDoesNotDrawTakesItDown() = runTest {
        val renderer = RecordingRenderer()
        val plugin = installed(renderer)

        plugin.load(ASS, null)
        // A WebVTT track is drawn by the cue overlay, not by libass. From this
        // plugin's side it is indistinguishable from any other selection it was
        // not asked to load — which is exactly why the rule is "anything I was
        // not handed, I stop drawing".
        emitSelection(plugin, track = 1.0)

        assertNull(plugin.subtitle(), "another renderer's track is showing and this one is still drawn")
    }

    @Test
    fun aSelectionThisPluginIsHandedSurvives() = runTest {
        val renderer = RecordingRenderer()
        val plugin = installed(renderer)

        plugin.load(ASS, null)
        // The consumer answers the selection by handing this plugin the new
        // styled track. Clearing unconditionally would wipe the track that was
        // just loaded, which is the ASS-to-ASS case that already worked and
        // must keep working.
        emitSelectionThenLoad(plugin, OTHER_ASS)

        assertEquals(OTHER_ASS, plugin.subtitle())
    }

    private suspend fun installed(renderer: RecordingRenderer): SubtitlePlugin {
        val player = ComposedPlayer(backend = null)
        player.setup(PlayerConfig())
        val plugin = SubtitlePlugin(renderer)
        player.addPlugin(plugin)
        held = player
        return plugin
    }

    private var held: ComposedPlayer? = null

    private fun emitSelection(plugin: SubtitlePlugin, track: Double?) {
        held?.emit(CoreEvents.Subtitle, SubtitlePayload(track = track))
    }

    private suspend fun emitSelectionThenLoad(plugin: SubtitlePlugin, url: String) {
        held?.emit(CoreEvents.Subtitle, SubtitlePayload(track = 2.0))
        plugin.load(url, null)
    }

    private companion object {
        const val ASS = "https://media.example.test/show/1/signs.ass"
        const val OTHER_ASS = "https://media.example.test/show/1/full.ass"
    }
}
