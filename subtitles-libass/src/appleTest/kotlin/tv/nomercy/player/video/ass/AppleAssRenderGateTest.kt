// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val FRAME_WIDTH = 1920
private const val FRAME_HEIGHT = 1080

// The libass gate on Apple: does one real cue rasterize visible pixels.
//
// The same assertions as the desktop and Android gates, against the same
// contract, over a third binding — Kotlin/Native cinterop to a static libass
// built for the simulator. Three implementations answering the same questions
// the same way is the only evidence the contract is worth anything.
class AppleAssRenderGateTest {

    private fun renderer(): AssRenderer = assertNotNull(
        AssRenderers.create(AssPlatformContext()),
        "libass is linked in on Apple and still refused to start: ${AssRenderers.whyUnavailable()}",
    )

    @Test
    fun oneCueRasterizesVisiblePixelsAtItsOwnTimestamp() {
        val renderer: AssRenderer = renderer()
        try {
            renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
            renderer.loadTrack(skeletonAss())

            val frame: AssFrame = assertNotNull(
                renderer.render(INSIDE_CUE_MILLIS),
                "libass drew nothing inside the cue's own time range",
            )

            assertTrue(frame.images.isNotEmpty(), "the frame carried no images")
            assertTrue(
                frame.images.any { image -> image.pixels.any { it.toInt() != 0 } },
                "every image was fully transparent: the cue laid out but never rasterized",
            )
        } finally {
            renderer.release()
        }
    }

    @Test
    fun nothingIsDrawnBeforeTheCueStarts() {
        val renderer: AssRenderer = renderer()
        try {
            renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
            renderer.loadTrack(skeletonAss())

            val frame: AssFrame? = renderer.render(BEFORE_CUE_MILLIS)

            assertEquals(emptyList(), frame?.images.orEmpty(), "a cue drew before its start time")
        } finally {
            renderer.release()
        }
    }

    @Test
    fun theImagesCarryCoverageAndAPosition() {
        // The cinterop struct layout is generated rather than hand-written here,
        // but the same class of mistake is possible one level up: reading the
        // wrong field into the wrong name. A position on the frame and a
        // coverage buffer of stride times height is what says otherwise.
        val renderer: AssRenderer = renderer()
        try {
            renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
            renderer.loadTrack(skeletonAss())
            val frame: AssFrame = assertNotNull(renderer.render(INSIDE_CUE_MILLIS))

            for (image in frame.images) {
                assertTrue(image.width in 1..FRAME_WIDTH, "image width ${image.width} is not on the frame")
                assertTrue(image.height in 1..FRAME_HEIGHT, "image height ${image.height} is not on the frame")
                assertTrue(image.stride >= image.width, "stride ${image.stride} is narrower than the image")
                assertEquals(image.stride * image.height, image.pixels.size, "the coverage buffer is the wrong size")
            }
        } finally {
            renderer.release()
        }
    }

    @Test
    fun aReleasedRendererDrawsNothingRatherThanCrashing() {
        // Rendering into a freed libass context is a crash that takes the app
        // with it, and the report names neither this class nor the caller.
        val renderer: AssRenderer = renderer()
        renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
        renderer.loadTrack(skeletonAss())
        renderer.release()

        assertEquals(null, renderer.render(INSIDE_CUE_MILLIS))
    }
}
