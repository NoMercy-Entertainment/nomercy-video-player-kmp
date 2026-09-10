// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import tv.nomercy.player.core.controllers.ComposedPlayer

// The Compose half of a plugin's [tv.nomercy.player.core.plugin.ChromeContribution].
//
// commonMain declares WHERE a contribution goes; it cannot declare what it
// draws, because commonMain has no UI toolkit. A plugin that wants a slot on a
// Compose surface implements this on itself — the same shape HoldChromePlugin
// already uses for VideoUiPlugin's controller, generalised to any plugin id a
// registry names.
//
// Takes the player itself rather than a chrome's read projection, on purpose:
// [ChromeState]/[ChromeCommands] are the video chrome's own shape and the
// television chrome has a different one ([tv.nomercy.player.video.tv]'s
// TvTransportState/TvChromeController). A plugin drawing the same widget on
// both — a cast banner, a skip-intro button — needs one contract, and the
// player is the one thing both chromes already hold. A plugin that wants
// playback state collects the player's own stateFlow like any other Compose
// reader would.
public fun interface ComposeChromeContribution {
    @Composable
    public fun Render(player: ComposedPlayer)
}
