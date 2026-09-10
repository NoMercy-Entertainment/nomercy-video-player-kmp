// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.touch

import tv.nomercy.player.video.input.RecordingPlayerCommands
import kotlin.test.Test
import kotlin.test.assertEquals

// A double tap opens a seek run and every tap after it keeps seeking.
//
// Reported live: "my double tap activates but any subsequent tap does not
// increment the seek value, it stays at 10 no matter how many times I tap".
// Requiring a fresh double tap per step is the behaviour that produced that.
internal class TouchZoneRunTest {

    private val forward = 0.9f
    private val back = 0.1f
    private val middle = 0.5f

    private fun plugin(recorder: RecordingPlayerCommands) =
        TouchZonesPlugin(commands = recorder.commands, opts = TouchZoneOptions())

    @Test
    fun aSingleTapAloneDoesNotSeek() {
        val recorder = RecordingPlayerCommands()
        plugin(recorder).tap(forward, middle, atMs = 0)

        assertEquals(emptyList(), recorder.seeks)
    }

    @Test
    fun everyTapAfterTheDoubleKeepsSeeking() {
        val recorder = RecordingPlayerCommands()
        val zones = plugin(recorder)

        zones.tap(forward, middle, atMs = 0)
        zones.tap(forward, middle, atMs = 120)
        zones.tap(forward, middle, atMs = 240)
        zones.tap(forward, middle, atMs = 360)

        assertEquals(3, recorder.seeks.size)
        assertEquals(true, recorder.seeks.all { it > 0f })
    }

    @Test
    fun movingToTheOtherSideStartsOver() {
        val recorder = RecordingPlayerCommands()
        val zones = plugin(recorder)

        zones.tap(forward, middle, atMs = 0)
        zones.tap(forward, middle, atMs = 120)
        // The other zone: a fresh run, so this single tap seeks nothing yet.
        zones.tap(back, middle, atMs = 240)

        assertEquals(1, recorder.seeks.size)
    }

    @Test
    fun aRunThatGoesQuietIsOver() {
        val recorder = RecordingPlayerCommands()
        val zones = plugin(recorder)

        zones.tap(forward, middle, atMs = 0)
        zones.tap(forward, middle, atMs = 120)
        zones.tap(forward, middle, atMs = 5_000)

        assertEquals(1, recorder.seeks.size)
    }
}
