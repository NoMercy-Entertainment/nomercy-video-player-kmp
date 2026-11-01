// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import kotlin.math.roundToLong

// How far ahead of the playhead to ask libass for, so the picture it returns is
// right for the frame on screen when it lands: the time it took to rasterize the
// last one plus one display refresh. A paused video asks for the frame it shows.
internal class DisplayLead {
    private var renderMs: Double = 0.0
    private var frameMs: Double = 0.0

    fun observeRender(ms: Double) {
        renderMs = smooth(renderMs, ms.coerceIn(0.0, MAX_RENDER_MS))
    }

    fun observeFrame(ms: Double) {
        if (ms !in MIN_FRAME_MS..MAX_FRAME_MS) return
        frameMs = smooth(frameMs, ms)
    }

    fun leadMs(moving: Boolean): Long = if (moving) (renderMs + frameMs).roundToLong() else 0L

    private fun smooth(held: Double, sample: Double): Double =
        if (held == 0.0) sample else held + (sample - held) * WEIGHT

    private companion object {
        const val WEIGHT = 0.2
        const val MAX_RENDER_MS = 100.0
        const val MIN_FRAME_MS = 4.0
        const val MAX_FRAME_MS = 20.0
    }
}
