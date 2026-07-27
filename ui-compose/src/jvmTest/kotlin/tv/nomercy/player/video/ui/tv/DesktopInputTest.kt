// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import tv.nomercy.player.core.input.PlayerKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// A desktop reaching the same chrome.
//
// The state machine cannot tell a gamepad from a remote, and that is the point:
// what a desktop adds is another way to produce the same named keys, not another
// set of behaviours.
class DesktopInputTest {

    @Test
    fun aDirectionalPadIsADirectionalPadWhateverItIsAttachedTo() {
        assertEquals(PlayerKey.Left, playerKeyOf(GamepadButton.Left))
        assertEquals(PlayerKey.Right, playerKeyOf(GamepadButton.Right))
        assertEquals(PlayerKey.Up, playerKeyOf(GamepadButton.Up))
        assertEquals(PlayerKey.Down, playerKeyOf(GamepadButton.Down))
    }

    @Test
    fun aSelectsAndBGoesBack() {
        // The arrangement every console has used for twenty years. Swapping them
        // is the single most noticeable thing a controller can get wrong.
        assertEquals(PlayerKey.Center, playerKeyOf(GamepadButton.A))
        assertEquals(PlayerKey.Back, playerKeyOf(GamepadButton.B))
    }

    @Test
    fun aBuildWithNoGamepadLibrarySaysSoRatherThanPretending() {
        // The desktop JVM has no gamepad API of its own. Claiming support that
        // silently produces nothing is worse than an honest no: a chrome can
        // draw a hint for the keyboard instead.
        assertFalse(NoGamepad.isSupported)
        assertEquals(emptyList(), NoGamepad.poll())
    }

    @Test
    fun pollingItRepeatedlyIsStillSafe() {
        // Polled every frame by whatever drives the chrome, so it has to be free
        // and it has to never throw.
        repeat(POLLS) { assertEquals(emptyList(), NoGamepad.poll()) }
    }
}

private const val POLLS = 100
