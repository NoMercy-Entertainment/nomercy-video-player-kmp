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
import tv.nomercy.player.core.events.SubtitleCue
import tv.nomercy.player.core.events.SubtitleCueChange
import tv.nomercy.player.core.ports.CanonicalBackendEvent
import tv.nomercy.player.testing.FakeVideoBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The engine's own cues, reaching the player.
//
// Media3 has reported these through Player.Listener.onCues all along and
// AVFoundation through its legible output; neither was subscribed to, and there
// was no canonical name for the event even if they had been. So a viewer who
// selected a track the container already carried watched a film with the track
// decoding and no words on screen.
//
// Driven through the backend's own channel rather than by emitting the player
// event directly. Emitting CoreEvents.SubtitleCue here would test the renderer
// again and leave the forwarding — the part that was missing — unexercised.
class EngineSubtitleCuesTest {

    @Test
    fun theEnginesCuesReachThePlayersChannel() = runTest {
        val backend = FakeVideoBackend()
        val player = NMVideoPlayer(backend, backend)
        player.setup()

        val seen: MutableList<SubtitleCueChange> = mutableListOf()
        player.on(CoreEvents.SubtitleCue) { seen += it }

        backend.fire(
            CanonicalBackendEvent.SUBTITLE_CUE,
            SubtitleCueChange(listOf(SubtitleCue(text = LINE, plainText = LINE)), language = "nl"),
        )

        assertEquals(listOf(LINE), seen.single().cues.map { it.text })
        assertEquals("nl", seen.single().language)
    }

    @Test
    fun anEmptyReportClearsThePicture() = runTest {
        // Both engines report an empty group when a cue ends and when the track
        // is deselected. Dropped as "no news", the last line of dialogue stays
        // on the picture until the next one replaces it.
        val backend = FakeVideoBackend()
        val player = NMVideoPlayer(backend, backend)
        player.setup()

        val seen: MutableList<SubtitleCueChange> = mutableListOf()
        player.on(CoreEvents.SubtitleCue) { seen += it }

        backend.fire(CanonicalBackendEvent.SUBTITLE_CUE, SubtitleCueChange())

        assertTrue(seen.single().cues.isEmpty())
    }

    @Test
    fun somethingThatIsNotACueChangeIsNotForwarded() = runTest {
        // A fabricated empty change would tell a renderer to clear the picture
        // on an engine that never had anything to say.
        val backend = FakeVideoBackend()
        val player = NMVideoPlayer(backend, backend)
        player.setup()

        val seen: MutableList<SubtitleCueChange> = mutableListOf()
        player.on(CoreEvents.SubtitleCue) { seen += it }

        backend.fire(CanonicalBackendEvent.SUBTITLE_CUE, "not a cue change")

        assertTrue(seen.isEmpty())
    }

    private companion object {
        const val LINE = "Wat is er met je hand gebeurd?"
    }
}
