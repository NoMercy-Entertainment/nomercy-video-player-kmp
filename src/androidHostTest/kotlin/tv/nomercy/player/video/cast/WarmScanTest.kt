// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class WarmScanTest {

    private class Token

    private class Recorder {
        val added = mutableListOf<Token>()
        val removed = mutableListOf<Token>()
    }

    private fun TestScope.scanOf(recorder: Recorder): WarmScan<Token> = WarmScan(
        scope = this,
        windowMs = WINDOW_MS,
        create = { Token() },
        add = { recorder.added += it },
        remove = { recorder.removed += it },
    )

    @Test
    fun theScanIsRemovedOnceAfterTheWindow() = runTest(StandardTestDispatcher()) {
        val recorder = Recorder()
        val scan = scanOf(recorder)

        scan.start()
        runCurrent()
        advanceTimeBy(WINDOW_MS + 1)
        runCurrent()

        assertEquals(1, recorder.removed.size)
        assertSame(recorder.added.single(), recorder.removed.single())
    }

    @Test
    fun startingTwiceInsideTheWindowAddsOnce() = runTest(StandardTestDispatcher()) {
        val recorder = Recorder()
        val scan = scanOf(recorder)

        scan.start()
        runCurrent()
        advanceTimeBy(WINDOW_MS / 2)
        scan.start()
        runCurrent()

        assertEquals(1, recorder.added.size)
        advanceTimeBy(WINDOW_MS)
        runCurrent()
    }

    @Test
    fun startingAfterTheWindowBeginsAFreshScan() = runTest(StandardTestDispatcher()) {
        val recorder = Recorder()
        val scan = scanOf(recorder)

        scan.start()
        runCurrent()
        advanceTimeBy(WINDOW_MS + 1)
        runCurrent()
        scan.start()
        runCurrent()

        assertEquals(2, recorder.added.size)
        advanceTimeBy(WINDOW_MS + 1)
        runCurrent()
        assertEquals(2, recorder.removed.size)
    }

    private companion object {
        const val WINDOW_MS = 45_000L
    }
}
