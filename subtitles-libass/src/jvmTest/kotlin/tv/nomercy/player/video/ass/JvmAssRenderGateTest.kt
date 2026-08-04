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
import tv.nomercy.player.video.subtitles.AssImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val FRAME_WIDTH = 1920
private const val FRAME_HEIGHT = 1080

// The libass gate on the desktop: does one real cue rasterize visible pixels.
//
// The same assertions as the Android gate, against the same contract, over a
// completely different binding — JNA to a system libass rather than JNI to a
// bundled one. That is the point of running it twice: a contract that only ever
// held on one implementation is a contract nobody has tested.
//
// Skipped, loudly, where libass is not installed. On Linux and macOS it is a
// package away; on Windows the only builds in circulation are the copies
// statically linked inside VLC and mpv, which cannot be loaded from outside
// them. A skip that says which is better than a red test that says the code is
// broken when the machine simply does not have the library.
//
// Not on a host that installed it on purpose, though — see LibassRequirement.
// There a skip means the install stopped working and the whole desktop path went
// untested behind a green tick.
class JvmAssRenderGateTest {

    private fun rendererOrSkip(): AssRenderer? = LibassRequirement.rendererOrSkip()?.let {
        assertNotNull(it, "no renderer on a platform that reports itself available")
    }

    @Test
    fun oneCueRasterizesVisiblePixelsAtItsOwnTimestamp() {
        val renderer: AssRenderer = rendererOrSkip() ?: return
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
        val renderer: AssRenderer = rendererOrSkip() ?: return
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
        // The struct's field order is the contract with C, and getting it wrong
        // does not crash — it reads whatever bytes are next and draws the
        // subtitle somewhere nonsensical. A position inside the frame and a
        // stride that matches the width is what says the layout was read
        // correctly rather than reinterpreted.
        val renderer: AssRenderer = rendererOrSkip() ?: return
        try {
            renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
            renderer.loadTrack(skeletonAss())
            val frame: AssFrame = assertNotNull(renderer.render(INSIDE_CUE_MILLIS))

            for (image in frame.images) {
                assertTrue(image.width in 1..FRAME_WIDTH, "image width ${image.width} is not on the frame")
                assertTrue(image.height in 1..FRAME_HEIGHT, "image height ${image.height} is not on the frame")
                assertTrue(image.stride >= image.width, "stride ${image.stride} is narrower than the image")
                assertTrue(image.x in -FRAME_WIDTH..FRAME_WIDTH, "image x ${image.x} is nowhere near the frame")
                assertTrue(image.y in -FRAME_HEIGHT..FRAME_HEIGHT, "image y ${image.y} is nowhere near the frame")
                assertEquals(image.stride * image.height, image.pixels.size, "the coverage buffer is the wrong size")
            }
        } finally {
            renderer.release()
        }
    }

    @Test
    fun aReleasedRendererDrawsNothingRatherThanCrashing() {
        // Rendering into a freed libass context is a segfault that takes the JVM
        // with it, and the crash log names neither this class nor the caller.
        val renderer: AssRenderer = rendererOrSkip() ?: return
        renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
        renderer.loadTrack(skeletonAss())
        renderer.release()

        assertEquals(null, renderer.render(INSIDE_CUE_MILLIS))
    }

    @Test
    fun theCuesFontNameChangesWhatIsDrawn() {
        val renderer: AssRenderer = rendererOrSkip() ?: return
        renderer.release()

        val named: List<AssImage> = draw(SKELETON_FONT)
        val other: List<AssImage> = draw("Courier New")

        assertTrue(named.isNotEmpty() && other.isNotEmpty(), "one of the renders drew nothing")
        assertTrue(named != other, "asking for a different font changed nothing")
    }

    private fun draw(font: String): List<AssImage> {
        val renderer: AssRenderer = AssRenderers.create(AssPlatformContext()) ?: return emptyList()
        try {
            renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
            renderer.loadTrack(skeletonAss(font))
            return renderer.render(INSIDE_CUE_MILLIS)?.images.orEmpty()
        } finally {
            renderer.release()
        }
    }
}
