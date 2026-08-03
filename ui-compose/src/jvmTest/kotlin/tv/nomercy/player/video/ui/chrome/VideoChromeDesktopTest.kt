// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.thumbnails.PreviewSprite
import tv.nomercy.player.video.ui.thumbnails.SpriteTileSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The two things only a desktop has: a pointer that comes and goes, and a
// keyboard.
//
// Desktop-only rather than in the shared gate, because a phone has neither and a
// test that pretended otherwise would be asserting against synthesised events no
// viewer can produce.
@OptIn(ExperimentalTestApi::class)
class VideoChromeDesktopTest {

    private fun ComposeUiTest.mountPlaying(sprite: PreviewSprite? = null): NMVideoPlayer {
        val player = NMVideoPlayer(RecordingVideoBackend())

        setContent {
            LaunchedEffect(player) {
                player.setup(PlayerConfig())
                player.queue(listOf(ChromeTestItem()))
                player.load(ChromeTestItem())
                // Playing, because three of the five autohide rules do nothing
                // at all while a film is paused: paused holds the chrome open,
                // and a test that left it paused would pass on that instead.
                player.play()
            }
            VideoChrome(player, FormFactor.Desktop, previewSprite = sprite)
        }
        waitForIdle()

        // The autohide is four seconds and a running test clock would step
        // straight through it, so a chrome woken by the pointer would be gone
        // again before the assertion looked at it.
        mainClock.autoAdvance = false
        return player
    }

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
    }

    @Test
    fun movingThePointerWakesTheChrome() = runComposeUiTest {
        mountPlaying()

        onNodeWithTag(DESKTOP_CHROME_TAG).performMouseInput { moveTo(center) }
        settle()

        onNodeWithTag(TRANSPORT_BAR_TAG).assertExists()
    }

    @Test
    fun andLeavingTheWindowTakesItAway() = runComposeUiTest {
        // Rule three. A pointer leaving is a stronger signal than one that has
        // merely stopped moving, so it does not wait out the four seconds.
        mountPlaying()

        onNodeWithTag(DESKTOP_CHROME_TAG).performMouseInput { moveTo(center) }
        settle()
        onNodeWithTag(DESKTOP_CHROME_TAG).performMouseInput { moveTo(Offset(-1f, -1f)) }
        settle()

        onNodeWithTag(TRANSPORT_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun aPointerRestingOnTheBarIsShownWhereItWouldLand() = runComposeUiTest {
        // `wireSliderBar` shows `.slider-pop` on `mouseover`, not only during a
        // drag. Without it a viewer with a mouse had to commit to a drag before the
        // player would tell them anything about where they were pointing.
        mountPlaying()

        onNodeWithTag(SCRUBBER_TAG).performMouseInput { moveTo(center) }
        settle()

        onNodeWithTag(SCRUB_PREVIEW_TAG).assertExists()
    }

    @Test
    fun andTheBubbleGoesWhenThePointerLeavesTheBar() = runComposeUiTest {
        // `mouseleave` sets `--visibility: 0` and resets every chapter marker's
        // hover fill to scaleX(0). Both stayed put, so the bar went on advertising a
        // position nobody was pointing at.
        //
        // Here rather than in the shared gate for the reason at the top of this
        // file, and it was measured rather than assumed: Robolectric's hover
        // synthesis lands the enter and drops the exit, so the Android host had the
        // bubble still up after a pointer had left. That is the emulator's account
        // of a mouse, not the chrome's behaviour.
        mountPlaying()

        onNodeWithTag(SCRUBBER_TAG).performMouseInput { moveTo(center) }
        settle()
        onNodeWithTag(SCRUBBER_TAG).performMouseInput { exit(center) }
        settle()

        onNodeWithTag(SCRUB_PREVIEW_TAG).assertDoesNotExist()
    }

    @Test
    fun andTheBubbleIsTallerThanTheEightUnitBarItHangsOver() = runComposeUiTest {
        // The test above asserts the bubble EXISTS, and it existed the whole time
        // it was invisible. The strip that hosts it is eight units tall — the
        // height of the drawn bar — and a Box of a fixed height caps every child
        // measured against it. The scrubber escapes with requiredHeight; the
        // bubble had no such escape, so the frame, the clock and the chapter name
        // were all coerced into an eight-unit sliver.
        //
        // Measured rather than asserted to exist, because that is the difference
        // between the green light this had and a green light that can go red.
        mountPlaying()

        onNodeWithTag(SCRUBBER_TAG).performMouseInput { moveTo(center) }
        settle()

        val bubble: Dp = onNodeWithTag(SCRUB_PREVIEW_TAG).getUnclippedBoundsInRoot().height
        assertTrue(bubble > STRIP_ROW_HEIGHT, "the bubble measured $bubble inside an 8dp row")
    }

    @Test
    fun andTheFrameUnderTheThumbIsDrawnAtTheSizeTheSheetDeclares() = runComposeUiTest {
        // The other half. A bubble with room in it still shows no picture if the
        // frame is measured to nothing, and the sprite path had never been driven
        // through the assembled chrome on this host at all — only through the
        // loader and the tile source, each on its own.
        mountPlaying(sprite = PreviewSprite(SPRITE_FRAMES, OneTile))

        onNodeWithTag(SCRUBBER_TAG).performMouseInput { moveTo(center) }
        settle()

        val frame: DpRect = onNodeWithTag(SCRUB_FRAME_TAG).getUnclippedBoundsInRoot()
        assertTrue(frame.height > STRIP_ROW_HEIGHT, "the frame measured ${frame.height}")
        assertTrue(frame.width > STRIP_ROW_HEIGHT, "the frame measured ${frame.width}")

        // And the pixels, off the renderer that actually draws them. A box of
        // the right size still shows nothing if the bitmap never reaches the
        // canvas, and every other assertion here is about layout.
        val pixels: PixelMap = onNodeWithTag(SCRUB_FRAME_TAG).captureToImage().toPixelMap()
        assertEquals(TILE_COLOUR, pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test
    fun spaceTogglesPlayback() = runComposeUiTest {
        // Through the key handler rather than through a binding of the view's
        // own, which is what keeps the desktop keys and the television keys the
        // same table with one group swapped.
        val player = mountPlaying()

        onNodeWithTag(DESKTOP_CHROME_TAG).requestFocus()
        onNodeWithTag(DESKTOP_CHROME_TAG).performKeyInput { pressKey(Key.Spacebar) }
        settle()

        assertEquals(PlayState.PAUSED, player.state().playState)
    }
}

private const val SETTLE_MS = 500L

private val SPRITE_FRAMES = listOf(
    SpriteCue(start = 0.0, end = 60.0, url = "s.webp", x = 0, y = 0, width = 320, height = 178),
)

// A tile of one flat colour, so a pixel read off the middle of the drawn frame
// is either that tile or it is not the tile.
private val TILE_COLOUR = Color(red = 0.2f, green = 0.7f, blue = 0.4f)

private object OneTile : SpriteTileSource {
    override fun frame(index: Int): ImageBitmap = ImageBitmap(320, 178).also { sheet ->
        Canvas(sheet).drawRect(
            Rect(Offset.Zero, Size(sheet.width.toFloat(), sheet.height.toFloat())),
            Paint().apply { color = TILE_COLOUR },
        )
    }

    override fun release() = Unit
}
