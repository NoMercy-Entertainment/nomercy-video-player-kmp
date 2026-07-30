// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

// Where a host puts its own control instead of ours.
//
// Guidance rather than walls. The chrome ships opinionated because most
// consumers want a player rather than a construction kit, and the ones who want
// something else should not have to fork the library or reimplement the state
// machine to get it. A slot replaces one piece; several slots replace most of
// it; the router replaces all of it. None of those is a special case.
//
// Every slot is handed the same state and the same commands the default gets,
// which is the part that makes this an override rather than an escape hatch. A
// replacement transport row still drives the engine through the same seam, so it
// cannot drift into reading the player directly and going stale.
public data class ChromeSlots(
    val topBar: ChromeSlot? = null,
    val transport: ChromeSlot? = null,
    val scrubber: ChromeSlot? = null,
    // Behind the picture rather than over it: an app with artwork for what is
    // playing draws it here while nothing has decoded yet. The library has no
    // image loader and no business having one — a backdrop is a URL an
    // application already knows how to fetch, and shipping a fetcher would be
    // shipping a second one beside the one it already has.
    val backdrop: ChromeSlot? = null,
    // Over everything, and additive rather than replacing: a skip-intro button
    // and a cast banner are the host's features, not the chrome's.
    val overlays: ChromeSlot? = null,
    // Any single image the chrome has a URL for and a box to put it in — today
    // the playlist card's thumbnail. Same reasoning as `backdrop` one field up,
    // and the same reason it is a slot rather than a fetcher: the card knows
    // where the picture goes and an application knows how to get one.
    //
    // Left unset the box keeps the web's own empty state, which is what the
    // browser shows when an `img` fails to load: the tinted panel, the shadow
    // and the progress overlay, without a broken-image glyph.
    val artwork: ChromeArtwork? = null,
)

// One piece of chrome, given what every piece is given.
public typealias ChromeSlot = @Composable (ChromeState, ChromeCommands) -> Unit

// One picture, and the box it has to fill.
//
// The modifier is not a courtesy: the caller has already sized and clipped the
// slot, so an implementation that ignores it draws outside the corner radius it
// was given. Fill it and nothing else.
public typealias ChromeArtwork = @Composable (url: String, modifier: Modifier) -> Unit

// So a host can furnish the slots once around its whole player screen rather
// than at each call. A composition local rather than a parameter because the
// thing wanting to override is usually a layer or two above the thing mounting
// the player, and threading it down is where one call site keeps the default and
// nobody notices.
public val LocalChromeSlots: ProvidableCompositionLocal<ChromeSlots> = compositionLocalOf { ChromeSlots() }
