// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.MediaBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private class InertReceiverBackend : MediaBackend {
    override suspend fun load(url: String, opts: LoadOptions) = Unit
    override suspend fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun release() = Unit
    override fun currentTime(): Double = 0.0
    override fun currentTime(seconds: Double) = Unit
    override fun duration(): Double = 0.0
    override fun volume(): Float = 1.0f
    override fun volume(value: Float) = Unit
    override fun mute() = Unit
    override fun unmute() = Unit
    override fun buffered(): Double = 0.0
    override fun playbackRate(): Double = 1.0
    override fun playbackRate(rate: Double) = Unit
    override fun state(): BackendState = BackendState.IDLE
    override fun on(event: String, fn: (Any?) -> Unit) = Unit
    override fun off(event: String, fn: (Any?) -> Unit) = Unit
}

private data class StateEventClip(override val id: String, override val url: String, override val title: String? = null) :
    PlaylistItem

class ReceiverStateEventTest {

    @Test
    fun anEmptyPlayerProjectsAsIdleWithNoPlaylist() = runTest {
        val player = ComposedPlayer(backend = InertReceiverBackend(), scope = backgroundScope)
        player.setup(PlayerConfig())

        val snapshot = projectReceiverState(player)

        assertEquals("idle", snapshot.playbackState)
        assertEquals(-1, snapshot.playlistActiveIndex)
        assertEquals(0, snapshot.playlistLength)
        assertFalse(snapshot.muted)
    }

    @Test
    fun aQueuedItemProjectsItsIdAndTitle() = runTest {
        val player = ComposedPlayer(backend = InertReceiverBackend(), scope = backgroundScope)
        player.setup(PlayerConfig())
        player.queue(listOf(StateEventClip("a", "https://example.test/a.mkv", "A Film")))

        val snapshot = projectReceiverState(player)

        assertEquals("a", snapshot.itemId)
        assertEquals("A Film", snapshot.itemTitle)
        assertEquals(1, snapshot.playlistLength)
        assertEquals(0, snapshot.playlistActiveIndex)
    }
}
