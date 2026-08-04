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
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val FRAME_WIDTH = 1920
private const val FRAME_HEIGHT = 1080

// The libass gate on Android: does one real cue rasterize visible pixels.
//
// Not "libass initialized" and not "the renderer returned an object". A libass
// binding that loads, accepts a track and draws nothing is the exact failure
// this slice exists to rule out, and every one of those steps succeeds while it
// happens.
class AndroidAssRenderGateTest {

    private fun renderer(): AssRenderer {
        val context = AssPlatformContext(InstrumentationRegistry.getInstrumentation().targetContext)
        return assertNotNull(
            AssRenderers.create(context),
            "no renderer on a platform that reports itself available: ${AssRenderers.whyUnavailable()}",
        )
    }

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
        // A renderer that draws the cue permanently passes the test above and
        // shows a subtitle over the whole film.
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
    fun aReleasedRendererDrawsNothingRatherThanCrashing() {
        // The binding frees its native contexts in a finalizer, so a render call
        // after release is a native crash reported against whatever app was
        // running. Refusing the call is the only thing this layer can do about
        // it, and it has to actually do it.
        val renderer: AssRenderer = renderer()
        renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
        renderer.loadTrack(skeletonAss())
        renderer.release()

        assertEquals(null, renderer.render(INSIDE_CUE_MILLIS))
    }

    @Test
    fun theCuesFontNameChangesWhatIsDrawn() {
        // Font selection working at all. A renderer that ignored the style's
        // font would draw both of these identically, and every subtitle in the
        // library would silently be in one typeface.
        val monospaced: List<AssImage> = draw(attach = null, font = SUBSTITUTE_FAMILY)
        val fallback: List<AssImage> = draw(attach = null, font = SKELETON_FONT)

        assertTrue(monospaced.isNotEmpty() && fallback.isNotEmpty(), "one of the renders drew nothing")
        assertTrue(
            monospaced != fallback,
            "asking for a different font changed nothing: the style's font is being ignored",
        )
    }

    @Test
    fun theNameGivenToAddFontIsNotWhatACueMatchesOn() {
        // The finding that cost this gate two runs, kept as a test so it cannot
        // be un-learned. libass matches a style's Fontname against the family
        // recorded inside the font file; the name passed to addFont is only a
        // label for the embedded blob. Attaching a real font under an invented
        // name therefore resolves nothing, which is exactly why the app carries
        // a TTF name parser instead of trusting the manifest's filename.
        val realFont: ByteArray = File(SUBSTITUTE_FONT).takeIf { it.exists() }?.readBytes()
            ?: error("no font at $SUBSTITUTE_FONT to attach")

        val underAnInventedName: List<AssImage> = draw(attach = realFont, font = SKELETON_FONT)
        val notAttachedAtAll: List<AssImage> = draw(attach = null, font = SKELETON_FONT)

        assertEquals(
            notAttachedAtAll,
            underAnInventedName,
            "attaching under an invented name resolved it: the alias matters after all, and " +
                "the font pipeline can stop parsing TTF name tables",
        )
    }

    private fun draw(attach: ByteArray?, font: String): List<AssImage> {
        val renderer: AssRenderer = renderer()
        try {
            attach?.let { renderer.addFont(font, it) }
            renderer.frameSize(FRAME_WIDTH, FRAME_HEIGHT)
            renderer.loadTrack(skeletonAss(font))
            return renderer.render(INSIDE_CUE_MILLIS)?.images.orEmpty()
        } finally {
            renderer.release()
        }
    }
}

// A monospace face, so a fallback sans and the attached font cannot coincide.
// Every Android build ships the file; the family name is what libass matches.
private const val SUBSTITUTE_FONT = "/system/fonts/DroidSansMono.ttf"
private const val SUBSTITUTE_FAMILY = "Droid Sans Mono"
