// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.core.cues.parseVttSprite

// Fetches a sprite sheet and its layout, and hands back something scrubbable.
//
// Everything fails to null. A preview is the part of a player that is allowed to
// be missing: a chrome draws no thumbnail and the viewer still scrubs, which is
// a far better answer than a scrubber that throws.
//
// [scope] is the player's, not this call's. The Android tile source keeps
// reading bands long after the load returns, and handing it the scope that ends
// when this function does would cancel the first band mid-decode.
public suspend fun loadPreviewSprite(
    spriteUrl: String,
    vttUrl: String,
    fetch: SpriteFetchers,
    scope: CoroutineScope,
    openTiles: (ByteArray, List<SpriteCue>, CoroutineScope) -> SpriteTileSource? = ::spriteTileSource,
): PreviewSprite? = coroutineScope {
    // Both at once. Two round trips to a self-hosted server over a home
    // connection is a visible wait before the first preview appears, and there
    // is nothing in either file that the other needs.
    val sheet: Deferred<ByteArray?> = async { fetch.bytes(spriteUrl) }
    val layout: Deferred<String?> = async { fetch.text(vttUrl) }

    val bytes: ByteArray? = sheet.await()

    // No cues is no preview rather than an empty one. A sheet for an item too
    // short to have frames, or a VTT that arrived truncated, would otherwise
    // become a sprite answering null to every time — a chrome drawing an empty
    // box under the scrubber for the whole title.
    // Against the VTT's own URL, which is what the web passes and what the cue
    // paths are written relative to. Resolving them against the SHEET happens to
    // agree whenever the two files sit in the same directory — which is the usual
    // case, and the reason a wrong base here would have stayed hidden.
    val frames: List<SpriteCue> = layout.await()?.let { parseVttSprite(it, vttUrl) }.orEmpty()

    if (bytes == null || frames.isEmpty()) {
        null
    } else {
        openTiles(bytes, frames, scope)?.let { PreviewSprite(frames, it) }
    }
}
