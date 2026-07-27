// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.player.ActionOptions
import tv.nomercy.player.core.player.ActionSource
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.video.FakeVideoBackend
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The claim this whole subsystem rests on: while casting, this phone is a remote
// control and not a second renderer.
//
// Every case that asserts the local engine stayed still is paired with one that
// asserts the command reached the television, and with one that shows the same
// action plays locally when nothing is being cast. Any of the three alone passes
// with the feature broken — the first with a player that does nothing at all,
// the second with both playing at once, the third with casting never engaged.
class VideoCastPluginTest {

    private class Rig(
        val player: NMVideoPlayer,
        val backend: FakeVideoBackend,
        val client: FakeTvControlClient,
        val plugin: VideoCastPlugin,
    )

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private suspend fun TestScope.rig(waker: CastWaker = FakeCastWaker()): Rig {
        val backend = FakeVideoBackend()
        val client = FakeTvControlClient()
        val scope: CoroutineScope = eager()
        val controller = RemoteVideoController(client, scope)
        val plugin = VideoCastPlugin(controller, scope, waker)
        val player = NMVideoPlayer(backend = backend, video = backend)

        player.setup(PlayerConfig())
        player.queue(listOf(VideoItem(id = "a", url = "https://media.example.test/a.mkv", title = "A")))
        player.addPlugin(plugin)
        return Rig(player, backend, client, plugin)
    }

    private fun device() = RemoteDevice(
        id = "dev-a",
        serviceName = "Living Room-04217",
        host = "192.168.1.40",
        port = 7626,
    )

    @Test
    fun withNothingBeingCastThePlayerPlaysHere() = runTest {
        // The control. Without it, every assertion below would also pass on a
        // player that simply never plays.
        val rig: Rig = rig()

        rig.player.play()

        assertTrue(rig.backend.playCount > 0, "the player did not play locally when nothing was being cast")
        assertEquals(emptyList(), rig.client.commands.filter { it == "play" })
    }

    @Test
    fun whileCastingPlayReachesTheTelevisionAndNotTheLocalEngine() = runTest {
        val rig: Rig = rig()
        rig.plugin.startCast(device(), "https://media.example.test/a.mkv")
        val playedBefore: Int = rig.backend.playCount

        rig.player.play()

        assertEquals(playedBefore, rig.backend.playCount, "the film also started playing on this device")
        assertTrue(rig.client.commands.contains("play"), "the television was never told to play")
    }

    @Test
    fun whileCastingASeekReachesTheTelevisionInItsOwnUnit() = runTest {
        val rig: Rig = rig()
        rig.plugin.startCast(device(), "https://media.example.test/a.mkv")
        val seekedBefore: Int = rig.backend.seekedTo.size

        rig.player.time(30.0)

        assertEquals(seekedBefore, rig.backend.seekedTo.size, "the local engine also seeked")
        assertTrue(rig.client.commands.contains("seek:30000"), "the television got ${rig.client.commands}")
    }

    @Test
    fun everyTransportVerbIsRoutedRatherThanJustPlay() = runTest {
        // A plugin that intercepted only play would leave a viewer able to
        // pause the phone and not the television.
        val rig: Rig = rig()
        rig.plugin.startCast(device(), "https://media.example.test/a.mkv")

        rig.player.pause()
        rig.player.next()
        rig.player.previous()

        assertTrue(rig.client.commands.containsAll(listOf("pause", "next", "previous")))
    }

    @Test
    fun whileCastingPauseDoesNotAlsoPauseTheLocalEngine() = runTest {
        val rig: Rig = rig()
        rig.plugin.startCast(device(), "https://media.example.test/a.mkv")
        val pausedBefore: Int = rig.backend.pauseCount

        rig.player.pause()

        assertEquals(pausedBefore, rig.backend.pauseCount)
    }

    @Test
    fun whatTheTelevisionDoesIsNotSentStraightBackToIt() = runTest {
        // The loop. A pause frame from the set updates the local player, which
        // raises its own before-pause — and without the guard that goes back to
        // the television that just sent it, forever.
        val rig: Rig = rig()
        rig.plugin.startCast(device(), "https://media.example.test/a.mkv")
        val before: Int = rig.client.commands.count { it == "pause" }

        rig.player.pause(ActionOptions(source = ActionSource.REMOTE))

        assertEquals(before, rig.client.commands.count { it == "pause" }, "an action from the set was echoed back")
    }

    @Test
    fun whatTheSetIsDoingIsWhatTheLocalChromeDraws() = runTest {
        // One source of truth. A phone drawing its own optimistic state and a
        // television doing something else is how a scrubber and a screen come
        // to disagree.
        val rig: Rig = rig()
        rig.client.session = RemotePlayerState(itemTitle = "Blade Runner 2049")
        rig.plugin.startCast(device(), "https://media.example.test/a.mkv")

        rig.client.emit(RemoteEvent.State(RemotePlayerState(itemTitle = "Dune", positionMs = 60_000)))

        assertEquals("Dune", rig.plugin.castState.value?.itemTitle)
        assertEquals(60_000, rig.plugin.castState.value?.positionMs)
    }

    @Test
    fun aTelevisionWithNoRouteIsReportedRatherThanCastTo() = runTest {
        // Launching at a set that cannot be reached leaves a phone showing a
        // casting state with a dark screen in the room.
        val rig: Rig = rig(waker = FakeCastWaker(WakeOutcome.NO_ROUTE))

        val outcome: WakeOutcome = rig.plugin.startCast(device(), "https://media.example.test/a.mkv")

        assertEquals(WakeOutcome.NO_ROUTE, outcome)
        assertTrue(rig.plugin.casting.not(), "a cast was started against a television with no route")
    }

    @Test
    fun stoppingTheCastReturnsControlToThisDevice() = runTest {
        val rig: Rig = rig()
        rig.plugin.startCast(device(), "https://media.example.test/a.mkv")

        rig.plugin.stopCast()
        rig.player.play()

        assertTrue(rig.plugin.casting.not(), "the plugin still thinks it is casting")
        assertTrue(rig.backend.playCount > 0, "the player did not resume playing locally")
    }

    @Test
    fun theSetDisconnectingReturnsControlWithoutAnyoneAskingItTo() = runTest {
        // Switched off at the wall, or another phone took it. A viewer pressing
        // play afterwards should get their film here rather than nothing.
        val rig: Rig = rig()
        rig.plugin.startCast(device(), "https://media.example.test/a.mkv")

        rig.client.emit(RemoteEvent.Device("disconnected"))
        rig.player.play()

        assertTrue(rig.backend.playCount > 0, "playback went nowhere after the television disconnected")
    }
}
