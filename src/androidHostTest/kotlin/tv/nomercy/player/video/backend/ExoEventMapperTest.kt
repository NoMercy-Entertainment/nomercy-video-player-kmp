// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.backend

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tv.nomercy.player.core.ports.CanonicalBackendEvent

// The ruler for Media3's state machine.
//
// Media3 does not emit the canonical events; it reports a state integer and an
// is-playing flag, and something has to decide which announcements those mean.
// That decision is the part most likely to be wrong and the part hardest to see
// when it is — a chrome bound to the wrong event just never updates, which
// looks like the chrome's bug.
//
// So it lives in a pure class with no engine in it, and this measures it
// directly. Driving a real ExoPlayer to check the same thing would need a
// device, would take seconds per case, and could not reach the ordering rules
// at all: an engine cannot be asked to become READY twice on demand.
class ExoEventMapperTest {

    @Test
    fun becomingReadyAnnouncesMetadataThenCanPlay() {
        val mapper = ExoEventMapper()

        val events: List<String> = mapper.onPlaybackState(Player.STATE_READY)

        // Both, in this order. A consumer reads duration on loadedmetadata and
        // enables its controls on canplay, so the reverse order offers a play
        // button for something whose length is not known yet.
        assertEquals(
            listOf(CanonicalBackendEvent.LOADED_METADATA, CanonicalBackendEvent.CAN_PLAY),
            events,
        )
    }

    @Test
    fun becomingReadyASecondTimeAnnouncesNothing() {
        // Media3 re-enters READY after every seek and every rebuffer. A player
        // that re-announced canplay would have its consumer re-run whatever it
        // does on first-playable — re-applying a saved position, re-showing the
        // controls — every time the network hiccuped.
        val mapper = ExoEventMapper()
        mapper.onPlaybackState(Player.STATE_READY)

        val second: List<String> = mapper.onPlaybackState(Player.STATE_READY)

        assertTrue(second.isEmpty(), "readiness was announced twice: $second")
    }

    @Test
    fun aNewLoadArmsReadinessAgain() {
        // The latch is per item, not per player. Without the reset, the second
        // thing a viewer plays never announces that it can play, and a queue
        // stops working after its first entry.
        val mapper = ExoEventMapper()
        mapper.onPlaybackState(Player.STATE_READY)

        mapper.onLoadStart()
        val afterReload: List<String> = mapper.onPlaybackState(Player.STATE_READY)

        assertEquals(
            listOf(CanonicalBackendEvent.LOADED_METADATA, CanonicalBackendEvent.CAN_PLAY),
            afterReload,
        )
    }

    @Test
    fun bufferingIsWaitingAndTheEndIsEnded() {
        val mapper = ExoEventMapper()

        assertEquals(listOf(CanonicalBackendEvent.WAITING), mapper.onPlaybackState(Player.STATE_BUFFERING))
        assertEquals(listOf(CanonicalBackendEvent.ENDED), mapper.onPlaybackState(Player.STATE_ENDED))
    }

    @Test
    fun idleAnnouncesNothing() {
        // STATE_IDLE is where the engine sits before prepare and after stop.
        // Neither is an event a consumer acts on, and mapping it to anything
        // would put a spurious announcement between two real ones.
        val mapper = ExoEventMapper()

        assertTrue(mapper.onPlaybackState(Player.STATE_IDLE).isEmpty())
    }

    @Test
    fun startingToPlayAnnouncesPlayThenPlaying() {
        val mapper = ExoEventMapper()

        val events: List<String> = mapper.onIsPlaying(true)

        // Media3 reports "is playing" rather than a play intent, and it stays
        // false through buffering. By the time it flips, both the request and
        // the fact are true — so both are announced, in the order the contract
        // has them, and a consumer listening for either sees it.
        assertEquals(listOf(CanonicalBackendEvent.PLAY, CanonicalBackendEvent.PLAYING), events)
    }

    @Test
    fun stoppingAnnouncesPause() {
        val mapper = ExoEventMapper()
        mapper.onIsPlaying(true)

        assertEquals(listOf(CanonicalBackendEvent.PAUSE), mapper.onIsPlaying(false))
    }

    @Test
    fun theSameIsPlayingValueTwiceAnnouncesOnce() {
        // Media3's callback can repeat a value across an onEvents batch. A
        // second pause with nothing between them would make a consumer counting
        // pauses — a resume-position writer, an analytics hook — record two.
        val mapper = ExoEventMapper()
        mapper.onIsPlaying(true)

        val repeated: List<String> = mapper.onIsPlaying(true)

        assertTrue(repeated.isEmpty(), "the same state was announced twice: $repeated")
    }

    @Test
    fun theWholeSpineComesOutInContractOrder() {
        // The sequence a consumer actually sees, assembled from the callbacks
        // Media3 fires in the order it fires them. The individual rules above
        // can each be right while the assembled order is wrong.
        val mapper = ExoEventMapper()
        val seen = mutableListOf<String>()

        seen += CanonicalBackendEvent.LOAD_START.also { mapper.onLoadStart() }
        seen += mapper.onPlaybackState(Player.STATE_BUFFERING)
        seen += mapper.onPlaybackState(Player.STATE_READY)
        seen += mapper.onIsPlaying(true)
        seen += mapper.onIsPlaying(false)

        assertEquals(
            listOf(
                CanonicalBackendEvent.LOAD_START,
                CanonicalBackendEvent.WAITING,
                CanonicalBackendEvent.LOADED_METADATA,
                CanonicalBackendEvent.CAN_PLAY,
                CanonicalBackendEvent.PLAY,
                CanonicalBackendEvent.PLAYING,
                CanonicalBackendEvent.PAUSE,
            ),
            seen,
        )
    }
}
