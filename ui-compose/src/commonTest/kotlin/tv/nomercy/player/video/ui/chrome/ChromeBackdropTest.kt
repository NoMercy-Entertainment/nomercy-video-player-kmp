// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// When the backdrop is up, against TrailerMobileUiPlugin's own condition.
//
// The obvious reading — "show it when not playing" — is wrong in a way that is
// obvious the moment somebody pauses and artwork covers the film. These pin the
// two states that separate the right rule from that one.
class ChromeBackdropTest {

    @Test
    fun itIsUpBeforeTheDurationIsKnown() {
        assertTrue(backdropIsVisible(durationSeconds = 0.0, currentSeconds = 0.0))
    }

    // A loaded item sitting at the very start has a duration and no frame yet.
    @Test
    fun andWhileTheItemSitsAtItsStart() {
        assertTrue(backdropIsVisible(durationSeconds = 1200.0, currentSeconds = 0.0))
    }

    @Test
    fun itIsGoneOnceTheFilmHasMoved() {
        assertFalse(backdropIsVisible(durationSeconds = 1200.0, currentSeconds = 0.5))
    }

    // The one that separates the real rule from "not playing". A pause has a
    // frame on screen; covering it with artwork is the bug this pins.
    @Test
    fun aPauseInTheMiddleDoesNotBringItBack() {
        assertFalse(backdropIsVisible(durationSeconds = 1200.0, currentSeconds = 600.0))
    }

    // A negative duration is what an engine reports for a live stream, and it is
    // <= 0 rather than == 0 for exactly that reason.
    @Test
    fun aLiveStreamKeepsItUp() {
        assertTrue(backdropIsVisible(durationSeconds = -1.0, currentSeconds = 30.0))
    }
}
