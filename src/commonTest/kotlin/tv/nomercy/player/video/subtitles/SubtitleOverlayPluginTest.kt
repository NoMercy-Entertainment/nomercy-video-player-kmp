// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.events.ALIGN_END
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.events.SubtitleCue
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

        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(listOf(CUE)))

        assertEquals(1, overlay.boxes.value.size)
        assertEquals(LINE, overlay.boxes.value.first().text)
    }

    @Test
    fun andNoCueClearsThem() = runTest {
        // The producer says "nothing is showing" by sending an empty list. Read
        // as "no news", the last line of dialogue would stay on the picture
        // until the next one replaced it.
        val (player, overlay) = mounted()

        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(listOf(CUE)))
        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange())

        assertTrue(overlay.boxes.value.isEmpty())
    }

    @Test
    fun aCueWithNoLineLandsWhereAViewerLooksForOne() = runTest {
        val (player, overlay) = mounted()

        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(listOf(CUE)))

        val box: CueBox = overlay.boxes.value.first()
        assertEquals(DEFAULT_LINE_PERCENT, box.linePercent)
        assertEquals(CueAlign.Center, box.align)
    }

    // The reason the event was widened.
    //
    // The channel used to carry ONE CueEvent — a time range and a string — so a
    // producer that had read `align:end line:10%` off a .vtt had nowhere to put
    // it, and every cue in the player landed centred at the bottom whatever the
    // file said. A forced sign placed at the top of the frame to stay clear of
    // the dialogue would have been drawn on top of the dialogue.
    @Test
    fun theCuesPositioningSurvivesTheEvent() = runTest {
        val (player, overlay) = mounted()

        player.emit(
            CoreEvents.SubtitleCue,
            SubtitleCueChange(listOf(SubtitleCue(text = SIGN, plainText = SIGN, line = 10.0, align = ALIGN_END))),
        )

        val box: CueBox = overlay.boxes.value.first()
        assertEquals(10.0, box.linePercent)
        assertEquals(CueAnchor.Top, box.anchor)
        assertEquals(CueAlign.End, box.align)
    }

    // Two at once is the ordinary case, not an edge: a sign at the top of the
    // frame and dialogue at the bottom. The single-cue event could carry only
    // the first, so the second was dropped before any renderer saw it.
    @Test
    fun twoSimultaneousCuesBothReachTheScreen() = runTest {
        val (player, overlay) = mounted()

        player.emit(
            CoreEvents.SubtitleCue,
            SubtitleCueChange(listOf(CUE, SubtitleCue(text = SIGN, plainText = SIGN, line = 10.0))),
        )

        assertEquals(listOf(LINE, SIGN), overlay.boxes.value.map { it.text })
    }

    @Test
    fun theNextItemTakesTheLastLineWithIt() = runTest {
        // Otherwise the closing line of one film sits over the first frame of
        // the next, which reads as the new film having burnt-in subtitles.
        val (player, overlay) = mounted()

        player.emit(CoreEvents.SubtitleCue, SubtitleCueChange(listOf(CUE)))
        player.emit(CoreEvents.Item, ItemChange(item = null, index = 0))

        assertTrue(overlay.boxes.value.isEmpty())
    }

    private companion object {
        const val LINE = "It followed us here."
        const val SIGN = "— SINTEL —"
        val CUE = SubtitleCue(text = LINE, plainText = LINE)
    }
}
