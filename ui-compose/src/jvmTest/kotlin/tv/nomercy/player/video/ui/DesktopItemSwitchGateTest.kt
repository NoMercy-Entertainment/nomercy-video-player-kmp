// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.runBlocking
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.VlcjVideoBackend
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SWITCH_WAIT_MS = 15_000L
private const val POLL_MS = 50L
private const val SECOND_WIDTH = 96
private const val SECOND_HEIGHT = 48

// The second item's picture, on a player that already showed the first one.
//
// Reported 2026-08-03: the title said Tears of Steel, the audio was Tears of
// Steel, the clock was Tears of Steel, and the picture was the last frame of Big
// Buck Bunny. Every gate in this module loads ONE item into a fresh engine, so
// none of them could see it — a sink that receives nothing after a media change
// still passes a first-play test.
//
// Two sizes rather than two pictures. What reaches the sink is a bitmap, and its
// dimensions are the one property that cannot be produced by the previous item's
// frames still being drawn.
class DesktopItemSwitchGateTest {

    @Test
    fun theSecondItemsPictureReplacesTheFirsts() {
        if (!VlcjVideoBackend.isAvailable()) {
            println("skipped: ${VlcjVideoBackend.whyUnavailable()}")
            return
        }

        val temp: String = System.getProperty("java.io.tmpdir")
        val first: File = writeTestVideo("$temp/nomercy-switch-first.y4m")
        val second: File = writeTestVideo("$temp/nomercy-switch-second.y4m", SECOND_WIDTH, SECOND_HEIGHT)
        val backend = VlcjVideoBackend()
        val sink = ComposeFrameSink()

        try {
            backend.embeddedPlayer.videoFrameSink(sink)
            playAndAwait(backend, sink, first, FRAME_WIDTH)
            playAndAwait(backend, sink, second, SECOND_WIDTH)
        } finally {
            backend.release()
            first.delete()
            second.delete()
        }
    }

    private fun playAndAwait(backend: VlcjVideoBackend, sink: ComposeFrameSink, media: File, width: Int) {
        runBlocking {
            backend.load(media.toURI().toString(), LoadOptions())
            backend.play()
        }
        assertEquals(width, awaitWidth(sink, width), "the picture never became ${media.name}'s")
    }

    private fun awaitWidth(sink: ComposeFrameSink, width: Int): Int {
        val deadline: Long = System.currentTimeMillis() + SWITCH_WAIT_MS
        var seen = 0
        while (System.currentTimeMillis() < deadline) {
            val current: ImageBitmap? = sink.frame.value
            seen = current?.width ?: 0
            if (seen == width) return seen
            Thread.sleep(POLL_MS)
        }
        return seen
    }
}
