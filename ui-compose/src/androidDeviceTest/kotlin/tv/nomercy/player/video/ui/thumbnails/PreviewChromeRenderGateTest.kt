// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import org.junit.Rule
import org.junit.Test
import tv.nomercy.player.core.chapters.ChapterController
import tv.nomercy.player.core.cues.ChapterCues
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.video.ui.chrome.CHAPTER_BAR_TAG
import tv.nomercy.player.video.ui.chrome.ChapterBarState
import tv.nomercy.player.video.ui.chrome.ChapterProgressBar
import kotlin.test.assertTrue

// Does the preview chrome actually paint, on a real device.
//
// Every piece of this has a unit test and none of them can answer the question.
// A bar that computes perfect marker positions and draws none of them passes all
// of them, and so does a preview that resolves the right frame and never puts it
// on screen. These are Canvas draws rather than a punched-through SurfaceView,
// so unlike the engine render gate the pixels really are in the view and can be
// read back.
//
// Run on a phone (SM-A137F, API 34) and on an Android TV box (Nokia Streaming
// Box 8010, API 34). CI has no device attached.
class PreviewChromeRenderGateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theBarPaintsAFilledChapterDifferentlyFromAnUnplayedOne() {
        // Half way through a real chapter list. The left of the bar is behind
        // the playhead and the right is ahead of it, and they are drawn at
        // different opacities — so if the segments painted at all, the two ends
        // of the same bar do not match.
        val controller = ChapterController { "Chapter" }
        controller.durationChanged(DURATION)
        controller.ingest(ChapterCues.parse(REAL_CHAPTERS_VTT))

        compose.setContent {
            ChapterProgressBar(
                state = ChapterBarState(
                    currentSeconds = DURATION / 2.0,
                    duration = DURATION,
                    bufferedFraction = 0.75,
                    chapters = controller.chapters(),
                ),
                onSeek = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
        compose.waitForIdle()

        // The bar's own pixels, found by its tag rather than by capturing the whole
        // surface. The root is mostly background, and reading a quarter of the way
        // across it is reading the test host as often as the bar.
        val painted: Bitmap = compose.onNodeWithTag(CHAPTER_BAR_TAG).captureToImage().asAndroidBitmap()
        val middleRow: Int = painted.height / 2

        // A quarter in and three quarters in: one side of the playhead each,
        // far enough from it that a rounded end or an anti-aliased edge is not
        // what is being read.
        val behind: Int = painted.getPixel(painted.width / QUARTER, middleRow)
        val ahead: Int = painted.getPixel(painted.width * THREE / QUARTER, middleRow)

        assertTrue(
            AndroidColor.alpha(behind) > 0,
            "the bar drew nothing at all behind the playhead",
        )
        assertTrue(
            behind != ahead,
            "played and unplayed parts of the bar are the same colour, so no fill was drawn",
        )
    }

    @Test
    fun theScrubPreviewPutsTheFramesPixelsOnScreen() {
        // A tile source handing back a solid red frame. If the preview paints
        // what it was given, red reaches the screen; if it paints the box and
        // never the image, it does not.
        val red: ImageBitmap = solidBitmap(AndroidColor.RED)
        val sprite = PreviewSprite(FRAMES, FixedTiles(red))

        compose.setContent {
            SpriteFramePreview(sprite = sprite, seconds = 15.0, modifier = Modifier.testTag(PREVIEW))
        }
        compose.waitForIdle()

        // The preview itself, not the root. The root is the whole test surface
        // and its centre is background, which reads as "the frame is missing"
        // whether it is or not.
        val painted: Bitmap = compose.onNodeWithTag(PREVIEW).captureToImage().asAndroidBitmap()
        val centre: Int = painted.getPixel(painted.width / 2, painted.height / 2)

        assertTrue(
            AndroidColor.red(centre) > CHANNEL_MAJORITY,
            "the frame's pixels never reached the screen: centre was ${Integer.toHexString(centre)}",
        )
        assertTrue(
            AndroidColor.green(centre) < CHANNEL_MAJORITY,
            "something other than the frame was drawn over it",
        )
    }

    @Test
    fun aFrameStillBeingReadDrawsNothingRatherThanCrashing() {
        // The band has not landed. This is the common case on the first scrub of
        // a title, and a preview that threw here would take the scrubber down
        // mid-drag on every playback.
        val sprite = PreviewSprite(FRAMES, FixedTiles(null))

        compose.setContent {
            SpriteFramePreview(sprite = sprite, seconds = 15.0, modifier = Modifier.testTag(PREVIEW))
        }
        compose.waitForIdle()

        val painted: Bitmap = compose.onNodeWithTag(PREVIEW).captureToImage().asAndroidBitmap()

        assertTrue(painted.width > 0, "the preview did not lay out without an image")
    }
}

private fun solidBitmap(color: Int): ImageBitmap =
    Bitmap.createBitmap(TILE_EDGE, TILE_EDGE, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color) }
        .asImageBitmap()

private class FixedTiles(private val pixels: ImageBitmap?) : SpriteTileSource {
    override fun frame(index: Int): ImageBitmap? = pixels
    override fun release() = Unit
}

private const val PREVIEW = "sprite-preview"
private const val QUARTER = 4
private const val THREE = 3
private const val DURATION = 1_435.0
private const val SHEET_URL = "s.webp"
private const val TILE_EDGE = 16
private const val CHANNEL_MAJORITY = 128

private val FRAMES = listOf(
    SpriteCue(start = 0.0, end = 10.0, url = SHEET_URL, x = 0, y = 0, width = 320, height = 178),
    SpriteCue(start = 10.0, end = 20.0, url = SHEET_URL, x = 320, y = 0, width = 320, height = 178),
)

private val REAL_CHAPTERS_VTT = """
WEBVTT

Chapter 1
00:00:00 --> 00:00:33
Prologue
Chapter 2
00:00:33 --> 00:02:03
Opening
Chapter 3
00:02:03 --> 00:12:11
Part A
Chapter 4
00:12:11 --> 00:12:18
Eyecatch
Chapter 5
00:12:18 --> 00:21:51
Part B
Chapter 6
00:21:51 --> 00:23:21
Ending
Chapter 7
00:23:21 --> 00:23:55
Epilogue
""".trimIndent()
