// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Where the picture actually is inside the view.
//
// Subtitles belong on the video, not on the view. A 21:9 film in a 16:9 window
// has black bars, and a subtitle positioned against the view sits in one of
// them — which is how a caption ends up half off the bottom of a phone.
public data class VideoRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

// The letterbox arithmetic, the downscale, and what counts as a seek.
//
// All pure, so all provable without a renderer. Every one of these is a decision
// the Android view and the Swift host both made separately, and having them
// agree is only possible if they are the same function.
public object RenderGeometry {

    // The video's box inside a view of the given size.
    //
    // An unknown aspect fills the view. That is the honest answer before the
    // first frame reports its size, and it is also the branch that makes
    // priming from the script matter: without it, subtitles span edge to edge
    // for the first moments of every item and then jump inwards.
    public fun computeVideoRect(viewWidth: Int, viewHeight: Int, sourceAspect: Float): VideoRect {
        if (sourceAspect <= 0f || viewWidth <= 0 || viewHeight <= 0) {
            return VideoRect(0, 0, viewWidth.coerceAtLeast(0), viewHeight.coerceAtLeast(0))
        }

        val viewAspect: Float = viewWidth.toFloat() / viewHeight.toFloat()

        return if (sourceAspect >= viewAspect) {
            // Wider than the view: full width, bars above and below.
            val height: Int = (viewWidth / sourceAspect).roundToInt().coerceAtMost(viewHeight)
            VideoRect(left = 0, top = (viewHeight - height) / 2, width = viewWidth, height = height)
        } else {
            // Taller than the view: full height, bars left and right.
            val width: Int = (viewHeight * sourceAspect).roundToInt().coerceAtMost(viewWidth)
            VideoRect(left = (viewWidth - width) / 2, top = 0, width = width, height = viewHeight)
        }
    }

    // How big to actually render, given a pixel budget.
    //
    // The subtitle layer renders below video resolution and is upscaled by the
    // GPU, which is free. Rendering at full 4K is not: it roughly halves the
    // time spent inside libass on a low-end device, and that time is on the
    // frame path.
    //
    // Never upscales. A budget larger than the rect means render it as it is,
    // not stretch it to fill a number.
    public fun computeRenderTarget(rectWidth: Int, rectHeight: Int, maxPixels: Int): Pair<Int, Int> {
        if (rectWidth <= 0 || rectHeight <= 0) return 0 to 0

        val area: Long = rectWidth.toLong() * rectHeight.toLong()
        if (area <= maxPixels.toLong()) return rectWidth to rectHeight

        val scale: Double = sqrt(maxPixels.toDouble() / area.toDouble())
        // At least one pixel each way. A rect scaled to zero renders nothing and
        // reads as a subtitle track that silently stopped working.
        return (rectWidth * scale).toInt().coerceAtLeast(1) to (rectHeight * scale).toInt().coerceAtLeast(1)
    }

    // The size to give libass before the real video size is known.
    //
    // An .ass script declares the resolution it was authored against, and every
    // position in it is relative to that. Handing libass nothing until the first
    // frame arrives means the opening cue is laid out against a guess.
    //
    // The real video size wins later. This is a starting point, not an override.
    public fun primeStorageFromScript(playResX: Int, playResY: Int): Pair<Int, Int>? =
        if (playResX > 0 && playResY > 0) playResX to playResY else null

    // Whether the playhead jumped rather than advanced.
    //
    // A seek invalidates everything rendered ahead, and a scheduler that missed
    // one keeps showing the cue from where the viewer used to be. The threshold
    // is generous on purpose: a dropped frame or a slow tick is not a seek, and
    // treating it as one throws away work every time the device stutters.
    public fun isSeek(previousMs: Long, currentMs: Long, thresholdMs: Long = SEEK_THRESHOLD_MS): Boolean {
        val delta: Long = currentMs - previousMs
        // Backwards by any amount is a seek. Time does not run backwards on its
        // own, so even a small negative step is the viewer having moved.
        return delta < 0L || delta > thresholdMs
    }

    // A second. Long enough that no ordinary stutter reaches it, short enough
    // that a skip-back button is caught on the next tick.
    public const val SEEK_THRESHOLD_MS: Long = 1_000L

    // The largest rect that fits both dimensions, for a caller that has a
    // maximum in each direction rather than a pixel budget.
    public fun fitWithin(rectWidth: Int, rectHeight: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
        if (rectWidth <= 0 || rectHeight <= 0) return 0 to 0

        val scale: Double = min(
            maxWidth.toDouble() / rectWidth.toDouble(),
            maxHeight.toDouble() / rectHeight.toDouble(),
        ).coerceAtMost(1.0)

        return (rectWidth * scale).toInt().coerceAtLeast(1) to (rectHeight * scale).toInt().coerceAtLeast(1)
    }
}
