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
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.media.PlaylistItem
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * A loaded ASS track does not survive the item that carried it.
 *
 * The plugin registered no listeners at all — `use()` was never overridden — so
 * a track loaded for one film stayed in the renderer when the queue moved on,
 * and libass went on drawing that film's dialogue against the next one's
 * playhead.
 *
 * Photographed on the desktop testbed: Big Buck Bunny, a Blender short with no
 * Japanese dialogue, rendering an anime karaoke line in romaji over its opening.
 * The sidecar CueTracker had already been fixed for exactly this and this is the
 * other renderer, on its own path, with the same bug.
 */
class SubtitleClearsOnItemChangeTest {

    @Test
    fun movingToAnotherItemDropsTheTrack() = runTest {
        val renderer = RecordingRenderer()
        val player = ComposedPlayer(backend = null)
        player.setup(PlayerConfig())
        val plugin = SubtitlePlugin(renderer)
        player.addPlugin(plugin)
        player.queue(listOf(Item("a"), Item("b")))

        plugin.load(SUBTITLE, null)
        // The load throws — this player has no backend — and that is fine and
        // deliberate: `item` is emitted when the cursor moves, before anything
        // opens a source, and the cursor moving is the whole trigger under test.
        // Wiring an engine here would be testing the engine.
        runCatching { player.item("b") }

        assertNull(plugin.subtitle(), "the previous item's track is still loaded")
    }

    // A minimal item, because the testing module's TestItem is not on this
    // module's test classpath and the only fields this case touches are the id
    // and a url the null backend never opens.
    private data class Item(
        override val id: String,
        override val url: String = "https://media.example.test/$id.mp4",
        override val title: String? = null,
    ) : PlaylistItem

    private companion object {
        const val SUBTITLE = "https://media.example.test/show/1/subs.ass"
    }
}
