// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WIDESCREEN = 1920f / 1080f
private const val ULTRAWIDE = 21f / 9f

// The geometry every renderer shares.
//
// Pure, so provable without a renderer — which is the point: the Android view
// and the Swift host each made these decisions separately, and the only way they
// agree is if they are the same function.
class RenderGeometryTest {

    @Test
    fun aWidescreenFilmInAPortraitViewGetsBarsAboveAndBelow() {
        // Subtitles belong on the video, not on the view. Positioned against the
        // view, a caption ends up in a black bar — half off the bottom of a
        // phone held upright.
        val rect: VideoRect = RenderGeometry.computeVideoRect(1080, 1920, WIDESCREEN)

        assertEquals(1080, rect.width)
        assertEquals(608, rect.height)
        assertEquals(0, rect.left)
        assertTrue(rect.top > 0, "a widescreen film in a tall view has no bars")
    }

    @Test
    fun anUltrawideFilmInAWidescreenViewStillGetsBars() {
        val rect: VideoRect = RenderGeometry.computeVideoRect(1920, 1080, ULTRAWIDE)

        assertEquals(1920, rect.width)
        assertTrue(rect.height < 1080)
        assertTrue(rect.top > 0)
    }

    @Test
    fun aTallSourceInAWideViewGetsBarsLeftAndRight() {
        // Vertical video, which arrives more often than it used to.
        val rect: VideoRect = RenderGeometry.computeVideoRect(1920, 1080, 9f / 16f)

        assertEquals(1080, rect.height)
        assertTrue(rect.width < 1920)
        assertTrue(rect.left > 0)
        assertEquals(0, rect.top)
    }

    @Test
    fun aSourceMatchingTheViewFillsItExactly() {
        assertEquals(VideoRect(0, 0, 1920, 1080), RenderGeometry.computeVideoRect(1920, 1080, WIDESCREEN))
    }

    @Test
    fun anUnknownAspectFillsTheView() {
        // The honest answer before the first frame reports its size, and the
        // branch that makes priming from the script matter: without it,
        // subtitles span edge to edge for the first moments and then jump.
        assertEquals(VideoRect(0, 0, 1920, 1080), RenderGeometry.computeVideoRect(1920, 1080, 0f))
    }

    @Test
    fun aViewWithNoSizeProducesNoRectRatherThanANegativeOne() {
        // A composable measured before layout. A negative width reaches libass
        // as an enormous unsigned number.
        val rect: VideoRect = RenderGeometry.computeVideoRect(0, 0, WIDESCREEN)

        assertEquals(VideoRect(0, 0, 0, 0), rect)
    }

    @Test
    fun aRectAboveTheBudgetIsScaledDown() {
        // The subtitle layer renders below video resolution and the GPU upscales
        // it for nothing. Rendering at full 4K is not free: it roughly doubles
        // the time inside libass on a low-end device, on the frame path.
        val (width, height) = RenderGeometry.computeRenderTarget(3840, 2160, 1280 * 720)

        assertTrue(width.toLong() * height <= 1280L * 720, "the target exceeded the budget: ${width}x$height")
        assertTrue(width > height, "the aspect ratio was not preserved")
    }

    @Test
    fun aRectInsideTheBudgetIsLeftAlone() {
        // Never upscales. A budget larger than the rect means render it as it
        // is, not stretch it to fill a number.
        assertEquals(1280 to 720, RenderGeometry.computeRenderTarget(1280, 720, 1920 * 1080))
    }

    @Test
    fun aScaledRectNeverCollapsesToNothing() {
        // A rect scaled to zero renders nothing, and reads as a subtitle track
        // that silently stopped working.
        val (width, height) = RenderGeometry.computeRenderTarget(3840, 2160, 1)

        assertTrue(width >= 1 && height >= 1)
    }

    @Test
    fun aScriptResolutionPrimesTheStorageSize() {
        // Every position in an .ass file is relative to the resolution it was
        // authored against. Handing libass nothing until the first frame means
        // the opening cue is laid out against a guess.
        assertEquals(1920 to 1080, RenderGeometry.primeStorageFromScript(1920, 1080))
    }

    @Test
    fun aScriptWithNoResolutionPrimesNothing() {
        // Rather than zero, which libass would take as a real size.
        assertNull(RenderGeometry.primeStorageFromScript(0, 0))
        assertNull(RenderGeometry.primeStorageFromScript(1920, 0))
    }

    @Test
    fun aJumpForwardIsASeek() {
        assertTrue(RenderGeometry.isSeek(10_000L, 45_000L))
    }

    @Test
    fun anOrdinaryFrameAdvanceIsNot() {
        // Twenty-four frames a second is about 42ms. Treating that as a seek
        // would throw away every rendered cue on every frame.
        assertTrue(!RenderGeometry.isSeek(10_000L, 10_042L))
    }

    @Test
    fun aStutterIsNotASeek() {
        // A dropped frame or a slow tick is not the viewer moving, and treating
        // it as one throws away work every time the device struggles — which is
        // exactly when it can least afford to redo it.
        assertTrue(!RenderGeometry.isSeek(10_000L, 10_900L))
    }

    @Test
    fun anyStepBackwardsIsASeek() {
        // Time does not run backwards on its own, so even a small negative step
        // is the viewer having moved. A threshold applied symmetrically would
        // miss a short skip-back and keep showing the cue from where they were.
        assertTrue(RenderGeometry.isSeek(10_000L, 9_800L))
    }

    @Test
    fun fittingWithinBoundsNeverUpscales() {
        assertEquals(640 to 360, RenderGeometry.fitWithin(640, 360, 1920, 1080))
    }

    @Test
    fun fittingWithinBoundsRespectsTheTighterDimension() {
        // A rect that fits the width and not the height has to shrink for the
        // height, or it renders off the bottom.
        val (width, height) = RenderGeometry.fitWithin(1920, 1080, maxWidth = 1920, maxHeight = 540)

        assertTrue(height <= 540)
        assertTrue(width <= 1920)
    }
}
