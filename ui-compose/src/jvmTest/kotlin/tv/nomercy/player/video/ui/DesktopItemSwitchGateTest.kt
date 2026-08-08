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
import tv.nomercy.player.core.ports.FrameSourceBackend
import tv.nomercy.player.core.ports.MpvVideoBackend
import tv.nomercy.player.core.ports.VideoBackend
import tv.nomercy.player.core.ports.engines.EngineSelection
import tv.nomercy.player.core.ports.engines.VideoEngines
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
        // The registry's engine, so this gate covers whichever one the desktop
        // actually ships with rather than the one that was current when it was
        // written.
        val decision: EngineSelection = VideoEngines.select()
        if (decision is EngineSelection.None) {
            println("skipped: ${decision.reason}")
            return
        }

        val temp: String = System.getProperty("java.io.tmpdir")
        val first: File = writeTestVideo("$temp/nomercy-switch-first.y4m")
        val second: File = writeTestVideo("$temp/nomercy-switch-second.y4m", SECOND_WIDTH, SECOND_HEIGHT)
        val backend: VideoBackend = (decision as EngineSelection.Chosen).provider.create()
        val sink = ComposeFrameSink()

        try {
            (backend as FrameSourceBackend).videoFrameSink(sink)
            playAndAwait(backend, sink, first, FRAME_WIDTH)
            playAndAwait(backend, sink, second, SECOND_WIDTH)
        } finally {
            // release() is not on the contract — it is not a playback call —
            // so the gate names the engine rather than leaving one alive with
            // its threads for the next test to inherit.
            (backend as? MpvVideoBackend)?.release()
            first.delete()
            second.delete()
        }
    }

    private fun playAndAwait(backend: VideoBackend, sink: ComposeFrameSink, media: File, width: Int) {
        runBlocking {
            // A path, not a file: URI. mpv opens either and libVLC opened
            // either; the path is the form both take without argument.
            backend.load(media.absolutePath, LoadOptions())
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
