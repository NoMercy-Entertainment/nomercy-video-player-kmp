// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.testing.FakeVideoBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The quality ladder is announced once the engine has one.
 *
 * `VideoBackendBridge.announceLevels` was public, emitted `VideoEvents.Levels`,
 * and was called by nothing at all — grep it and the only hit is its own
 * declaration. Declared-never-emitted, the same class of defect as `beforeLoad`.
 * A consumer building a quality menu from the event, as the reference documents,
 * waited forever while `qualityLevels()` sat there holding the answer.
 *
 * `mediaReady` is the moment, because it is already the moment the player uses
 * for exactly this: `applyDefaultTracks` reads the engine's lists there and the
 * comment beside it says asking any earlier reads two empty lists.
 */
class LevelsAreAnnouncedTest {

    @Test
    fun theLadderIsAnnouncedWhenTheEngineHasRead() = runTest {
        // A ladder, because the fake ships with an empty one and announceLevels
        // is deliberately silent on empty — a progressive file has no rungs and
        // an empty `levels` would tell a menu to rebuild around nothing. The
        // first version of this test forgot, and read as the fix not working.
        val engine = FakeVideoBackend()
        engine.levels = listOf(
            QualityLevel(height = 1080, bitrate = 6_000_000, codec = "avc1"),
            QualityLevel(height = 720, bitrate = 3_000_000, codec = "avc1"),
        )
        val player = NMVideoPlayer(engine, engine)
        val announced: MutableList<Int> = mutableListOf()
        player.on(VideoEvents.Levels) { change -> announced += change.levels.size }

        player.setup(PlayerConfig())
        player.queue(listOf(tv.nomercy.player.testing.TestItem("a")))
        player.item("a")

        assertTrue(announced.isNotEmpty(), "levels was never announced")
        assertEquals(engine.qualityLevels().size, announced.last())
    }
}
