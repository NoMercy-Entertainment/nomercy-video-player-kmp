// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The gesture set, against what MobileCenterOverlay actually binds.
//
// The zones themselves need a render to exercise; what can be held here without
// one is the shape of the contract, and that is where the port could go wrong
// quietly: a zone bound to the wrong corner is invisible until somebody
// double-taps to skip and the screen dims instead.
class ChromeGesturesTest {

    // Nothing bound and nothing assumed. A host that wires only the tap gets a
    // grid where the other eight zones fall through to it rather than one that
    // silently seeks.
    @Test
    fun everyGestureIsAbsentUntilAHostBindsIt() {
        val gestures = ChromeGestures()

        assertNull(gestures.onTogglePlay)
        assertNull(gestures.onSeekBack)
        assertNull(gestures.onSeekForward)
        assertNull(gestures.onBrightnessUp)
        assertNull(gestures.onBrightnessDown)
        assertNull(gestures.onVolumeUp)
        assertNull(gestures.onVolumeDown)
    }

    // The app's grid: brightness down the LEFT edge and volume down the RIGHT,
    // seek either side of the middle row, play in the centre. Getting a pair the
    // wrong way round is the failure this pins.
    @Test
    fun theZonesCarryWhatTheAppsGridCarries() {
        val fired: MutableList<String> = mutableListOf()

        val gestures = ChromeGestures(
            onShowControls = { fired += "show" },
            onTogglePlay = { fired += "play" },
            onSeekBack = { fired += "seekBack" },
            onSeekForward = { fired += "seekForward" },
            onBrightnessUp = { fired += "brightnessUp" },
            onBrightnessDown = { fired += "brightnessDown" },
            onVolumeUp = { fired += "volumeUp" },
            onVolumeDown = { fired += "volumeDown" },
        )

        // Top row, left to right.
        gestures.onBrightnessUp?.invoke()
        gestures.onVolumeUp?.invoke()
        // Middle row.
        gestures.onSeekBack?.invoke()
        gestures.onTogglePlay?.invoke()
        gestures.onSeekForward?.invoke()
        // Bottom row.
        gestures.onBrightnessDown?.invoke()
        gestures.onVolumeDown?.invoke()

        assertEquals(
            listOf(
                "brightnessUp",
                "volumeUp",
                "seekBack",
                "play",
                "seekForward",
                "brightnessDown",
                "volumeDown",
            ),
            fired,
        )
    }

    // Showing the controls is the one thing every cell does, so it is the only
    // callback that is not nullable — a grid that could be built without it
    // would be a picture that swallows taps.
    @Test
    fun showingTheControlsIsAlwaysBound() {
        var shown = false

        ChromeGestures(onShowControls = { shown = true }).onShowControls()

        assertTrue(shown)
    }
}
