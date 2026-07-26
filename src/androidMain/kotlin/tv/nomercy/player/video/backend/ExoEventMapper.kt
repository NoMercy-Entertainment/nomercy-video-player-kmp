// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.backend

import androidx.media3.common.Player
import tv.nomercy.player.core.ports.CanonicalBackendEvent

// Media3's state machine, translated into the events every engine announces.
//
// Media3 does not emit the canonical events. It reports a state integer and an
// is-playing flag, and something has to decide what those mean. That decision
// is both the most likely thing to be wrong and the hardest to notice when it
// is: a chrome bound to an event that never arrives simply never updates, which
// reads as the chrome's bug.
//
// So it is a class with no engine in it. Everything here is a pure function of
// what Media3 last said, which is what lets the ordering rules be tested at all
// — an engine cannot be asked to become READY twice on demand, and those repeat
// cases are exactly where the rules live.
//
// It returns events rather than emitting them so the caller owns the bus. A
// mapper that emitted would have to be handed one, and then testing it would
// mean asserting on a fake bus rather than on the answer.
public class ExoEventMapper {

    // Readiness is announced once per item, not once per player.
    //
    // Media3 re-enters READY after every seek and every rebuffer. A consumer
    // that re-ran its first-playable work each time — restoring a saved
    // position, revealing the controls — would do it again on every network
    // hiccup, and the position restore would fight the viewer's own seek.
    private var announcedCanPlay: Boolean = false

    // Media3 can repeat an is-playing value across an onEvents batch, and a
    // consumer counting pauses to write a resume position would record two.
    private var lastIsPlaying: Boolean? = null

    // Called when a new item starts loading, which re-arms readiness. Without
    // this the second thing a viewer plays never announces that it can play,
    // and a queue stops working after its first entry.
    public fun onLoadStart() {
        announcedCanPlay = false
        lastIsPlaying = null
    }

    public fun onPlaybackState(state: Int): List<String> = when (state) {
        Player.STATE_BUFFERING -> listOf(CanonicalBackendEvent.WAITING)
        Player.STATE_ENDED -> listOf(CanonicalBackendEvent.ENDED)
        Player.STATE_READY -> readyEvents()
        // STATE_IDLE is where the engine sits before prepare and after stop.
        // Neither is something a consumer acts on, and announcing it would put
        // a spurious event between two real ones.
        else -> emptyList()
    }

    // Metadata before canplay, because a consumer reads duration on the first
    // and enables its controls on the second. Reversed, it offers a play button
    // for something whose length is not known yet.
    private fun readyEvents(): List<String> {
        if (announcedCanPlay) return emptyList()
        announcedCanPlay = true
        return listOf(CanonicalBackendEvent.LOADED_METADATA, CanonicalBackendEvent.CAN_PLAY)
    }

    // Media3 reports the fact of playing rather than the intent, and it stays
    // false through buffering. By the time it flips, both the request and the
    // fact are true — so both are announced, in the order the contract has
    // them, and a consumer listening for either one sees it.
    public fun onIsPlaying(isPlaying: Boolean): List<String> {
        if (lastIsPlaying == isPlaying) return emptyList()
        lastIsPlaying = isPlaying

        return if (isPlaying) {
            listOf(CanonicalBackendEvent.PLAY, CanonicalBackendEvent.PLAYING)
        } else {
            listOf(CanonicalBackendEvent.PAUSE)
        }
    }
}
