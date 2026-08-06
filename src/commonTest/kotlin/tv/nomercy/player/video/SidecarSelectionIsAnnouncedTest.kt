// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.testing.FakeVideoBackend
import tv.nomercy.player.testing.TestItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Choosing a sidecar track announces THAT track, not "off".
 *
 * The engine is told null for a sidecar, which is right — it has no such track
 * and asking for one leaves the picture blank. The EVENT was carrying the same
 * null, so every sidecar selection announced itself as captions off.
 *
 * Two consequences, and the second is the one that showed on screen. Any
 * consumer keying on `subtitle` saw "off" for a track that was in fact playing.
 * And selecting off AFTER a sidecar changed nothing, because the announcement
 * was already null — which is why turning captions off left the libass raster
 * frozen exactly where it was, on a paused frame, with the click landing and
 * the menu closing normally.
 */
class SidecarSelectionIsAnnouncedTest {

    @Test
    fun aSidecarSelectionAnnouncesItsOwnIndex() = runTest {
        val player = ready()
        val seen: MutableList<Double?> = mutableListOf()
        player.on(CoreEvents.Subtitle) { seen += it.track }

        player.addSubtitleTrack(SIDECAR)
        player.subtitle(SIDECAR)

        assertEquals(listOf<Double?>(player.subtitles().indexOf(SIDECAR).toDouble()), seen)
    }

    @Test
    fun turningCaptionsOffAfterASidecarIsADifferentAnnouncement() = runTest {
        val player = ready()
        player.addSubtitleTrack(SIDECAR)
        player.subtitle(SIDECAR)

        val seen: MutableList<Double?> = mutableListOf()
        player.on(CoreEvents.Subtitle) { seen += it.track }
        player.subtitle(null)

        // Null, and distinguishable from the selection before it. Both being
        // null is what made the off click a no-op.
        assertEquals(listOf<Double?>(null), seen)
    }

    private suspend fun ready(): NMVideoPlayer {
        val engine = FakeVideoBackend()
        val player = NMVideoPlayer(engine, engine)
        player.setup(PlayerConfig())
        // An item, because addSubtitleTrack attaches to the one playing and
        // refuses when there is none.
        player.queue(listOf(TestItem("a")))
        player.item("a")
        return player
    }

    private companion object {
        val SIDECAR = SubtitleTrack(
            id = "en-ass",
            language = "en",
            label = "English (Full)",
            format = "ass",
            url = "https://media.example.test/show/1/signs.ass",
        )
    }
}
