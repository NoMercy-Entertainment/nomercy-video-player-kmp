// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

// That the SECOND frame reaches the screen.
//
// Every existing check on this path grades one frame: the picture arrives, it
// lands in the right place, it is not black. All of them pass on a surface that
// draws frame one and then never changes again — which is what the desktop did,
// with FrameStats reporting delivered=23.9/s and drawn=23.9/s the whole time.
// Counters cannot see it, because the counters are of frames handed over and of
// draw passes run, and both of those really were happening.
//
// So this renders the composable, delivers a second frame of a different colour
// through the real sink, renders again, and reads the pixels back. There is no
// way to satisfy it except by the new frame being what gets drawn.
class FrameCanvasAdvancesTest {

    @Test
    fun aSecondFrameReplacesTheFirstOnScreen() {
        val sink = ComposeFrameSink()
        sink.format(CANVAS_SIDE, CANVAS_SIDE)

        val scene = ImageComposeScene(width = CANVAS_SIDE, height = CANVAS_SIDE) {
            FrameCanvas(sink, Modifier.fillMaxSize())
        }

        try {
            sink.deliver(solidFrame(GREEN))
            assertEquals(GREEN, scene.centrePixel(1), "the first frame never reached the screen")

            sink.deliver(solidFrame(RED))
            assertEquals(RED, scene.centrePixel(2), "the picture froze on the frame before it")
        } finally {
            scene.close()
        }
    }

    // A frame arrives on libVLC's thread, and this test hands one over on its
    // own. Either way the sink writes Compose state from outside a composition,
    // and a write made there is not visible to the next render until the global
    // snapshot advances — which normally happens on its own, a moment later.
    //
    // A moment later is fine for a player and useless for an assertion. This
    // failed once in a full parallel suite and passed alone every time, which is
    // that race exactly: under load the notification had not landed before the
    // second render, so the scene drew frame one and the test read the defect it
    // exists to catch. Advancing the snapshot here makes the handover
    // deterministic without weakening what is being asserted.
    private fun ComposeFrameSink.deliver(buffer: ByteBuffer) {
        accept(buffer)
        Snapshot.sendApplyNotifications()
    }

    // The colour in the middle of the rendered scene at [frame], as an ARGB int.
    //
    // The middle, because ContentScale.Fit letterboxes and the bars are black
    // whatever the frame holds — a corner would read the same on a live picture
    // and a dead one.
    //
    // The clock has to move. `render()` with no argument renders at the same
    // instant every time, and a state write that arrived between two renders is
    // then applied on one host and not on another: this passed on Windows and
    // failed on the Linux runner at the SECOND frame, which reads exactly like
    // the defect it is written to catch. A real player has a frame clock that
    // advances, so the test gives it one.
    private fun ImageComposeScene.centrePixel(frame: Long): Int =
        render(frame * NANOS_PER_FRAME)
            .toComposeImageBitmap()
            .toPixelMap()[CANVAS_SIDE / 2, CANVAS_SIDE / 2]
            .toArgb()

    // One flat colour, in the byte order libVLC's RV32 delivers: BGRA in memory
    // on a little-endian machine, which is what the sink is written against.
    private fun solidFrame(argb: Int): ByteBuffer {
        val buffer: ByteBuffer = ByteBuffer
            .allocateDirect(CANVAS_SIDE * CANVAS_SIDE * PIXEL_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)

        repeat(CANVAS_SIDE * CANVAS_SIDE) { buffer.putInt(argb) }
        buffer.rewind()
        return buffer
    }
}

private const val CANVAS_SIDE = 64

// 24 frames a second, which is what the desktop's own clip runs at.
private const val NANOS_PER_FRAME = 41_666_667L

private const val PIXEL_BYTES = 4

private const val GREEN = 0xFF00FF00.toInt()
private const val RED = 0xFFFF0000.toInt()
