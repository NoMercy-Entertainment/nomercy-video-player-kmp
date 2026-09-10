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
import tv.nomercy.player.core.plugin.ChromeSlot
import tv.nomercy.player.core.plugin.ContributionBinding

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
// bar, the transport row) goes through [ChromeSlotResolution] instead.
@Composable
public fun PluginOverlayContributions(
    player: ComposedPlayer?,
    slot: ChromeSlot = ChromeSlot.Overlay,
) {
    if (player == null) return

    val renderers: List<ComposeChromeContribution> = player.contributions(slot)
        .filterNot { it.contribution.replaces }
        .mapNotNull { player.getPlugin(it.pluginId) as? ComposeChromeContribution }

    for (renderer: ComposeChromeContribution in renderers) {
        renderer.Render(player)
    }
}

// P26.14/P27.14 — the three tiers a slot like TopBar or Transport resolves
// through, in precedence order: an application's own [hostOverride] wins
// outright (it is an explicit choice the app made about its own screen, and
// a plugin the app also chose to install has no claim over that); failing
// that, a plugin's [tv.nomercy.player.core.plugin.ChromeContribution.replaces]
// takes the built-in over; failing that, [default] — the chrome's own widget,
// unchanged.
//
// A slot with no host-override concept at all (SettingsMenu has no field on
// [ChromeSlots]) leaves [hostOverride] at its default and gets the same
// plugin-vs-built-in resolution without a host tier to check first.
//
// A `replaces` contribution whose plugin has no [ComposeChromeContribution]
// renderer on this platform falls through to [default] rather than drawing
// nothing — the same "skip rather than crash" rule [PluginOverlayContributions]
// already applies, because a slot that vanished because a plugin is real on
// every platform but this one is worse than the built-in nobody asked to
// replace showing instead.
@Composable
public fun ChromeSlotResolution(
    player: ComposedPlayer?,
    slot: ChromeSlot,
    context: ChromeSlotContext,
    hostOverride: (@Composable (ChromeState, ChromeCommands) -> Unit)? = null,
    default: @Composable () -> Unit,
) {
    if (hostOverride != null) {
        hostOverride(context.state, context.commands)
        return
    }

    val replacing: ContributionBinding? =
        player?.contributions(slot).orEmpty().firstOrNull { it.contribution.replaces }
    val renderer: ComposeChromeContribution? =
        replacing?.let { player?.getPlugin(it.pluginId) as? ComposeChromeContribution }

    if (renderer != null && player != null) {
        renderer.Render(player)
    } else {
        default()
    }
}
