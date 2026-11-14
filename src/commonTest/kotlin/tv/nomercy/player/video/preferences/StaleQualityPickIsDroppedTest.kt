// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.preferences

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.InMemoryStorage
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.testing.FakeVideoBackend
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A quality row written by the build that saved the rung the engine still
 * reported is never restored, and a pick saved since then still is.
 */
class StaleQualityPickIsDroppedTest {

    private class Rig(
        val player: NMVideoPlayer,
        val backend: FakeVideoBackend,
        val plugin: VideoPreferencesPlugin,
    )

    private suspend fun rig(storage: InMemoryStorage): Rig {
        val backend = FakeVideoBackend()
        val player = NMVideoPlayer(backend = backend, video = backend, storage = storage)
        player.setup(PlayerConfig())
        player.queue(listOf(VideoItem(id = "a", url = "https://media.example.test/a.m3u8", title = "A")))
        backend.levels = listOf(fullHd, hd)

        val plugin = VideoPreferencesPlugin(player, VideoPreferencesOptions())
        player.addPlugin(plugin)
        return Rig(player, backend, plugin)
    }

    @Test
    fun aPickSavedUnderTheOldKeyIsNotRestored() = runTest {
        val storage = InMemoryStorage()
        val rig: Rig = rig(storage)
        storage.set(OLD_QUALITY_KEY, OLD_ROW)

        rig.plugin.restore()

        assertNull(rig.player.quality(), "a pick the old build saved wrong pinned the item")
    }

    @Test
    fun theOldKeyIsRemovedOnTheFirstRead() = runTest {
        val storage = InMemoryStorage()
        val rig: Rig = rig(storage)
        storage.set(OLD_QUALITY_KEY, OLD_ROW)

        rig.plugin.restore()

        assertNull(storage.get(OLD_QUALITY_KEY), "the old row stayed in the store")
    }

    @Test
    fun aPickSavedUnderTheNewKeyComesBackOnTheNextLoad() = runTest {
        val storage = InMemoryStorage()
        val first: Rig = rig(storage)
        first.player.quality(fullHd)
        first.plugin.awaitWrites()

        val saved: String? = storage.get(QUALITY_KEY)
        assertEquals("""{"auto":false,"height":$FULL_HD_HEIGHT}""", saved, "the pick was not saved under the new key")
        assertNull(storage.get(OLD_QUALITY_KEY), "the pick was saved under the old key")

        val next: Rig = rig(storage)
        next.plugin.restore()

        assertEquals(FULL_HD_HEIGHT, next.player.quality()?.height, "the saved pick did not come back on the next load")
    }
}

private const val FULL_HD_HEIGHT = 1080
private const val HD_HEIGHT = 720

// The plugin's storage is namespaced by its id; these are the keys in the
// player's root store, the same one the device preferences file holds.
private const val QUALITY_KEY = "nmplayer-video-preferences-quality-v2"
private const val OLD_QUALITY_KEY = "nmplayer-video-preferences-quality"
private const val OLD_ROW = """{"auto":false,"height":$HD_HEIGHT}"""

private val fullHd = QualityLevel(height = FULL_HD_HEIGHT, bitrate = 6_000_000, codec = "avc1")
private val hd = QualityLevel(height = HD_HEIGHT, bitrate = 2_500_000, codec = "avc1")
