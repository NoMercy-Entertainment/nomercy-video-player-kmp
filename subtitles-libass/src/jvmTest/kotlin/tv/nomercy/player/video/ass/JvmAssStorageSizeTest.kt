// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import kotlin.test.Test
import kotlin.test.assertEquals

// The space a track was authored in, which a surface needs before it can decide
// how big to rasterize.
//
// Drawing at the size of the overlay is what makes a cue in a windowed player
// thick and blobby: the glyphs scale, and the outline, shadow and blur around
// them do not. The surface avoids that by rasterizing at the track's own
// resolution and scaling the result — which it can only do if the renderer says
// what that resolution is.
class JvmAssStorageSizeTest {

    @Test
    fun aLoadedTrackReportsTheResolutionItWasAuthoredAgainst() {
        val renderer: AssRenderer = LibassRequirement.rendererOrSkip() ?: return
        try {
            renderer.frameSize(640, 360)
            renderer.loadTrack(skeletonAss())

            assertEquals(
                AssSize(1920, 1080),
                renderer.storageSize(),
                "the track declares PlayRes 1920x1080 and the renderer forgot it",
            )
        } finally {
            renderer.release()
        }
    }

    @Test
    fun aHostThatKnowsTheVideoSizeOutranksTheScript() {
        // And keeps outranking it. A track loaded afterwards must not quietly
        // take the answer back to its own guess.
        val renderer: AssRenderer = LibassRequirement.rendererOrSkip() ?: return
        try {
            renderer.storageSize(1280, 720)
            renderer.loadTrack(skeletonAss())

            assertEquals(AssSize(1280, 720), renderer.storageSize())
        } finally {
            renderer.release()
        }
    }
}
