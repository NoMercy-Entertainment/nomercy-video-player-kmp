// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import androidx.compose.ui.graphics.ImageBitmap
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.video.thumbnails.frameIndexAt
import tv.nomercy.player.video.thumbnails.spriteFrameHeight
import tv.nomercy.player.video.thumbnails.spriteFrameWidth

// A sprite sheet a chrome can scrub against: which frame a time lands on, and
// the pixels for it once they have been read.
//
// This is the type that used to weld the whole video event hierarchy to Android.
// It carried an android.graphics.Bitmap, so every consumer of a player event
// needed the Android toolchain whether or not it wanted a thumbnail. Compose
// Multiplatform's ImageBitmap is the same picture without the tie, and keeping
// this in the Compose module rather than the video library's commonMain means
// the library itself still depends on no UI toolkit at all.
//
// Open, like everything a consumer might reasonably want to sit on top of. A
// chrome that wants to log every frame request, or serve one from its own cache
// while the band lands, should not have to fork the class to do it.
public open class PreviewSprite(
    public val frames: List<SpriteCue>,
    private val tiles: SpriteTileSource,
) {

    // What the sheet SAYS a frame measures.
    //
    // Kept because a chrome has to size and shape the preview box before any
    // pixels exist — the first frame is still being read while the box is being
    // laid out, and a box that resizes when the image lands is a box that jumps
    // under the viewer's thumb.
    public val frameWidthPx: Int = spriteFrameWidth(frames)

    public val frameHeightPx: Int = spriteFrameHeight(frames)

    public open fun cueAt(seconds: Double): SpriteCue? =
        frameIndexAt(frames, seconds)?.let(frames::get)

    // Null means "not yet", not "never". The band behind this frame is being
    // read; drawing nothing this pass and being recomposed when it lands is the
    // whole reason the sheet is not decoded up front.
    public open fun frameAt(seconds: Double): ImageBitmap? =
        frameIndexAt(frames, seconds)?.let(tiles::frame)

    public open fun release() {
        tiles.release()
    }
}
