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
import tv.nomercy.player.core.plugin.ChromeContribution
import tv.nomercy.player.core.plugin.ChromeSlot
import tv.nomercy.player.core.plugin.ContributionBinding

// The Compose half of a plugin's [ChromeContribution].
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

// Every contribution a plugin declared for [slot], resolved and drawn in the
// registry's own order.
//
// A contribution whose plugin does not implement [ComposeChromeContribution]
// is skipped rather than crashing: a plugin can be real on every other
// platform and simply not have a Compose renderer yet, which is a gap to
// close, not a reason to bring a chrome down.
//
// Additive only — every entry draws, none replaces the chrome's own widgets.
// [ChromeSlot.Overlay] is the slot this is safe for by construction: a
// skip-intro button and a cast banner are the host's features sitting over
// the picture, not a takeover of it. A slot where `replaces` matters (the top
// bar, the transport row) needs the calling chrome to check the first
// binding's `replaces` before drawing its own default, which is a decision
// left to whichever slot grows a real replacing contribution first rather
// than guessed at here.
@Composable
public fun PluginOverlayContributions(
    player: ComposedPlayer?,
    slot: ChromeSlot = ChromeSlot.Overlay,
) {
    if (player == null) return

    val bindings: List<ContributionBinding> = player.contributions(slot)
    for (binding: ContributionBinding in bindings) {
        val contribution: ChromeContribution = binding.contribution
        if (contribution.replaces) continue

        val renderer: ComposeChromeContribution =
            player.getPlugin(binding.pluginId) as? ComposeChromeContribution ?: continue
        renderer.Render(player)
    }
}
