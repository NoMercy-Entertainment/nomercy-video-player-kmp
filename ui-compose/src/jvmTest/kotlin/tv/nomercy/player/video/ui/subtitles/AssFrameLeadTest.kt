// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.subtitles.AssFrame
import tv.nomercy.player.video.subtitles.AssRenderer
import tv.nomercy.player.video.subtitles.AssSize
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AssFrameLeadTest {

    // The host hands over the time of the frame on screen. A lead on top of it
    // draws the sign for a later frame than the one the viewer sees.
    @Test
    fun aMovingVideoIsRenderedForTheTimeOfTheFrameItWasGiven() = runComposeUiTest {
        val renderer = SlowRenderer()
        val given: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())
        var presentedMs = START_MS

        setContent {
            Box(modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp)) {
                AssSubtitleLayer(
                    renderer = renderer,
                    positionMs = {
                        presentedMs += FRAME_MS
                        given += presentedMs
                        presentedMs
                    },
                )
            }
        }
        waitUntil(timeoutMillis = TIMEOUT_MS) { renderer.asked.size >= SAMPLES }

        val off: List<Long> = renderer.asked.toList().filter { it !in given }
        assertTrue(off.isEmpty(), "asked for times that no frame had: $off")
    }

    @Test
    fun aPausedVideoIsRenderedForTheFrameOnScreen() = runComposeUiTest {
        val renderer = SlowRenderer()

        setContent {
            Box(modifier = Modifier.width(WIDTH.dp).height(HEIGHT.dp)) {
                AssSubtitleLayer(renderer = renderer, positionMs = { PAUSED_AT_MS })
            }
        }
        waitUntil(timeoutMillis = TIMEOUT_MS) { renderer.asked.size >= SAMPLES }

        assertEquals(setOf(PAUSED_AT_MS), renderer.asked.toSet())
    }
}

private class SlowRenderer : AssRenderer {
    val asked: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    override fun addFont(name: String, data: ByteArray): Unit = Unit

    override fun clearFonts(): Unit = Unit

    override fun loadTrack(assContent: String): Unit = Unit

    override fun storageSize(): AssSize? = null

    override fun storageSize(width: Int, height: Int): Unit = Unit

    override fun frameSize(width: Int, height: Int): Unit = Unit

    override fun render(timeMillis: Long): AssFrame {
        asked += timeMillis
        Thread.sleep(RENDER_MS)
        return AssFrame(images = emptyList(), changed = false)
    }

    override fun release(): Unit = Unit
}

private const val WIDTH = 640
private const val HEIGHT = 360
private const val TIMEOUT_MS = 20_000L
private const val SAMPLES = 40
private const val RENDER_MS = 30L
private const val START_MS = 115_000L
private const val FRAME_MS = 42L
private const val PAUSED_AT_MS = 90_000L
