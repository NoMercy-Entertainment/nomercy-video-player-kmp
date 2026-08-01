// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.CueEvent
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.events.SubtitleCueChange
import tv.nomercy.player.testing.FakeVideoBackend
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The overlay listening to the channel cues arrive on.
//
// It was caller-driven only, and nothing in the library called it: `show` had
// one caller and that caller was a test. So the flow a renderer reads was
// permanently empty, and the whole path — a ported layout, a ported style, a
// settings menu writing into it — added up to a picture with no words on it. A
// renderer alone does not fix that; something has to produce.
class SubtitleOverlayPluginTest {

    private suspend fun mounted(): Pair<NMVideoPlayer, SubtitleOverlayPlugin> {
        val overlay = SubtitleOverlayPlugin()
        val player = NMVideoPlayer(FakeVideoBackend())
        player.setup()
        player.addPlugin(overlay)

        return player to overlay
    }

    @Test
    fun aCueOnThePlayersChannelReachesTheBoxes() = runTest {
        val (player, overlay) = mounted()

        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(CUE))

        assertEquals(1, overlay.boxes.value.size)
        assertEquals(LINE, overlay.boxes.value.first().text)
    }

    @Test
    fun andNoCueClearsThem() = runTest {
        // The producer says "nothing is showing" by sending a change with no
        // cue. Read as "no news", the last line of dialogue would stay on the
        // picture until the next one replaced it.
        val (player, overlay) = mounted()

        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(CUE))
        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(cue = null))

        assertTrue(overlay.boxes.value.isEmpty())
    }

    @Test
    fun aCueWithNoLineLandsWhereAViewerLooksForOne() = runTest {
        // The core event has no room for WebVTT positioning, so the default is
        // what every cue arriving this way gets: near the bottom, centred.
        val (player, overlay) = mounted()

        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(CUE))

        val box: CueBox = overlay.boxes.value.first()
        assertEquals(DEFAULT_LINE_PERCENT, box.linePercent)
        assertEquals(CueAlign.Center, box.align)
    }

    @Test
    fun theNextItemTakesTheLastLineWithIt() = runTest {
        // Otherwise the closing line of one film sits over the first frame of
        // the next, which reads as the new film having burnt-in subtitles.
        val (player, overlay) = mounted()

        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(CUE))
        player.emit(CoreEvents.Item, ItemChange(item = null, index = 0))

        assertTrue(overlay.boxes.value.isEmpty())
    }

    private companion object {
        const val LINE = "It followed us here."
        val CUE = CueEvent(startTime = 0.0, endTime = 2.0, text = LINE)
    }
}
