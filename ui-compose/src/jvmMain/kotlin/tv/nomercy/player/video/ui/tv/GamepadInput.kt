// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import tv.nomercy.player.core.input.PlayerKey

// A controller plugged into a desktop.
//
// The same chrome, driven the same way: a gamepad's directional pad is a remote
// with better buttons, and the state machine behind this cannot tell them apart.
// That is the point of the seam — the chrome does not need to know.
//
// The desktop JVM has no gamepad API of its own, so reading one means a native
// library. This is the boundary that library plugs into, and until one is
// chosen the answer is honestly nothing.
public interface GamepadInput {

    // What is being held or pressed right now, as named keys. Polled rather than
    // pushed, because that is how every gamepad library on this platform works
    // and inverting it here would be inventing an event loop.
    public fun poll(): List<PlayerKey>

    public val isSupported: Boolean
}

// No gamepad, which is what a build without a native library has.
//
// A real object rather than a null so a caller wires it unconditionally, asks
// whether it is supported, and gets a safe answer either way. The chrome works
// with a keyboard regardless, so this degrades to the ordinary desktop rather
// than to a broken one.
public object NoGamepad : GamepadInput {

    override fun poll(): List<PlayerKey> = emptyList()

    override val isSupported: Boolean = false
}

// The mapping every gamepad library agrees on, kept here so the one that gets
// chosen only has to produce these names.
//
// A is select and B is back, which is the arrangement every console has used for
// twenty years. Swapping them is the single most noticeable thing a controller
// can get wrong.
public enum class GamepadButton { Up, Down, Left, Right, A, B }

public fun playerKeyOf(button: GamepadButton): PlayerKey = when (button) {
    GamepadButton.Up -> PlayerKey.Up
    GamepadButton.Down -> PlayerKey.Down
    GamepadButton.Left -> PlayerKey.Left
    GamepadButton.Right -> PlayerKey.Right
    GamepadButton.A -> PlayerKey.Center
    GamepadButton.B -> PlayerKey.Back
}
