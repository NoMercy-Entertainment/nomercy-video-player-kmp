// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

// What a slot's renderer reads and what it can ask for, together.
//
// One parameter rather than two because they are never apart: a host override
// takes both, every built-in widget takes both, and a call site that passed a
// state from one frame beside commands from another would be a bug no
// signature could catch.
public class ChromeSlotContext(
    public val state: ChromeState,
    public val commands: ChromeCommands,
)
