// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import tv.nomercy.player.core.cues.SpriteCue

// Hands out one scrub-preview frame at a time.
//
// A sprite sheet is a single image holding every preview frame in a title, which
// for a feature film runs to around nine thousand pixels across and decodes to
// well over a hundred megabytes. Holding that as one bitmap in order to show one
// frame is what forced the decoder to shrink the sheet on the way in, and
// shrinking the sheet shrinks every frame in it — the preview came out soft no
// matter what size the server rendered it at.
//
// So the sheet is never decoded whole. An implementation reads the region behind
// the frame being asked for and nothing else, which is why [frame] may answer
// null: the pixels are not in memory yet. Callers draw nothing that pass and are
// recomposed when the frame arrives.
public interface SpriteTileSource {

    // The frame at [index] at its stored resolution, or null while it is still
    // being read. Safe to call from a draw pass: it never blocks and never
    // decodes inline.
    public fun frame(index: Int): ImageBitmap?

    // Drops decoded frames and closes the underlying reader.
    public fun release()
}

// Opens a sheet for region reads, or returns null if the bytes are not an image
// this platform can decode a region out of.
//
// [scope] is where reads happen. It belongs to the caller because the caller
// knows when the player goes away, and a decode still running against a released
// sheet is the one way this can outlive its own memory.
public expect fun spriteTileSource(
    encodedSheet: ByteArray,
    frames: List<SpriteCue>,
    scope: CoroutineScope,
): SpriteTileSource?
