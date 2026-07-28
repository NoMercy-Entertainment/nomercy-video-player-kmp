// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.thumbnails

import tv.nomercy.player.core.cues.SpriteCue

// Where a scrub position lands in a sprite sheet.
//
// Kept here rather than beside the decoded bitmap because none of it needs one,
// and the two places that consume it cannot both hold one: the Compose chrome
// has an ImageBitmap and the SwiftUI chrome has a UIImage it decodes itself.
// Splitting at the bitmap is what lets both agree on the frame without either
// re-parsing the sheet — which is how Android and iOS came to have their own
// answers for the same scrub in the first place.

// The frame a viewer is scrubbing over: the last one that has started.
//
// Past the end holds the last frame rather than going blank. Sheets are
// generated at a fixed interval and stop where the encode stopped, so the tail
// of an item routinely has no frame of its own, and a preview that empties out
// there reads as broken rather than as finished.
public fun frameIndexAt(frames: List<SpriteCue>, seconds: Double): Int? {
    if (frames.isEmpty()) return null

    // The cue whose window contains this second, and the LAST cue when none
    // does. That fallback is the web's, from desktop-ui/helpers/sprite.ts
    // lookupCue, which does `find(...) ?? cues.at(-1)`.
    //
    // Past the end of the sheet it is obviously right. Before the FIRST cue —
    // reachable only when a sheet's first cue starts later than zero — it shows
    // the last frame of the item, which reads like a bug and is what the web
    // does. Matched deliberately rather than improved: a native player showing
    // a different thumbnail than the browser at the same scrub position is a
    // divergence a viewer sees, and the fix belongs on both sides at once.
    // Recorded in web-chrome-fidelity-spec.md.
    val containing: Int = frames.indexOfFirst { seconds >= it.start && seconds < it.end }

    return if (containing >= 0) containing else frames.lastIndex
}

public fun frameAt(frames: List<SpriteCue>, seconds: Double): SpriteCue? =
    frameIndexAt(frames, seconds)?.let(frames::get)

// The size a frame is declared to be, which is not the size it is decoded at.
//
// The sheet gets downsampled to fit a device's memory, so the drawn region has
// to be scaled back from these numbers. Reading them off the first cue keeps
// the declared space in one place; see the crop math for what uses it.
public fun spriteFrameWidth(frames: List<SpriteCue>): Int =
    frames.firstOrNull()?.width ?: FALLBACK_FRAME_WIDTH_PX

public fun spriteFrameHeight(frames: List<SpriteCue>): Int =
    frames.firstOrNull()?.height ?: FALLBACK_FRAME_HEIGHT_PX

// The shape of the preview box, which a chrome needs before any frame arrives.
//
// Falls back to sixteen by nine on a frame declaring no height. That is a VTT
// written by something that got the rect wrong, and dividing by it gives an
// infinite aspect — a box with no height on Compose, and a crash on the divide
// in some layout passes. Sixteen by nine is wrong in a way nobody notices;
// infinity is wrong in a way that takes the scrubber down.
public fun spriteFrameAspect(frames: List<SpriteCue>): Float {
    val height: Int = spriteFrameHeight(frames)
    if (height <= 0) return FALLBACK_ASPECT

    return spriteFrameWidth(frames).toFloat() / height.toFloat()
}

// What both existing chromes assume when a sheet arrives with no cues. A
// preview box sized zero is worse than one sized for a frame that never comes.
private const val FALLBACK_FRAME_WIDTH_PX = 320
private const val FALLBACK_FRAME_HEIGHT_PX = 178

private const val FALLBACK_ASPECT = 16f / 9f
