// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.transcode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SeekPosition
import tv.nomercy.player.core.ports.RealtimeEvent
import tv.nomercy.player.testing.FakePlayer
import tv.nomercy.player.testing.FakeRealtimeChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// That the gate actually holds, and that it lets go.
//
// A check that only asserted the seek eventually completes passes with the
// plugin deleted, because a seek nothing gates completes immediately. So each
// case here asserts the state BEFORE the thing that should release it as well
// as after: held while the encoder is behind, through once it has caught up,
// and through anyway when the wait has gone on too long.
class LiveTranscodingPluginTest {

    private class Rig(
        val player: FakePlayer,
        val channel: FakeRealtimeChannel,
        val plugin: LiveTranscodingPlugin,
    )

    private fun rig(scope: CoroutineScope): Rig {
        val channel = FakeRealtimeChannel()
        val player = FakePlayer(scope = scope, realtime = { _, _ -> channel })
        val plugin = LiveTranscodingPlugin(LiveTranscodingOptions(controlUrl = CONTROL_URL))
        player.plugins.register(plugin)
        return Rig(player, channel, plugin)
    }

    @Test
    fun aSeekPastTheEncoderIsHeldUntilTheServerSaysThatFarIsWritten() = runTest {
        val rig: Rig = rig(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        val seek = async { rig.player.dispatchBefore(CoreEvents.BeforeSeek, SeekPosition(time = FAR_AHEAD)) }
        runCurrent()
        assertFalse(seek.isCompleted, "the seek was let through before the encoder had written that far")

        rig.channel.deliver(RealtimeEvent.MESSAGE, progress(FAR_AHEAD + 1))
        runCurrent()
        assertTrue(seek.isCompleted, "the seek stayed held after the encoder reported it had passed the target")
    }

    // Holding forever is the worse failure.
    //
    // An encoder that dies mid-job leaves the player with a scrubber that does
    // nothing and no way for the viewer to find out why, which is worse than a
    // seek into a gap that stalls and recovers.
    @Test
    fun aSeekTheEncoderNeverReachesGoesThroughWhenTheWaitRunsOut() = runTest {
        val rig: Rig = rig(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        val seek = async { rig.player.dispatchBefore(CoreEvents.BeforeSeek, SeekPosition(time = FAR_AHEAD)) }
        runCurrent()
        assertFalse(seek.isCompleted, "the seek was never held, so the timeout proves nothing")

        testScheduler.advanceTimeBy(LiveTranscodingOptions().seekTimeoutMs + 1)
        runCurrent()
        assertTrue(seek.isCompleted, "the seek was held past the timeout it was given")
    }

    @Test
    fun aSeekInsideWhatIsAlreadyWrittenIsNotHeldAtAll() = runTest {
        val rig: Rig = rig(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        rig.channel.deliver(RealtimeEvent.MESSAGE, progress(FAR_AHEAD))

        val seek = async { rig.player.dispatchBefore(CoreEvents.BeforeSeek, SeekPosition(time = NEARBY)) }
        runCurrent()
        assertTrue(seek.isCompleted, "a seek into encoded video waited on the encoder anyway")
    }

    // The socket belongs to somebody else's server, and this player has to
    // survive whatever it sends.
    @Test
    fun trafficThePluginCannotReadLeavesTheHeadWhereItWas() = runTest {
        val rig: Rig = rig(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        rig.channel.deliver(RealtimeEvent.MESSAGE, progress(NEARBY))

        rig.channel.deliver(RealtimeEvent.MESSAGE, "}{ not json at all")
        rig.channel.deliver(RealtimeEvent.MESSAGE, """{"type":"progress"}""")
        rig.channel.deliver(RealtimeEvent.MESSAGE, 42)
        runCurrent()

        assertEquals(NEARBY, rig.plugin.transcodedTo.value, "unreadable traffic moved the encoder's write head")
    }

    private fun progress(seconds: Double): String =
        """{"type":"progress","jobId":"job-a","transcodedSeconds":$seconds}"""
}

private const val CONTROL_URL = "wss://encoder.test/jobs"
private const val FAR_AHEAD = 300.0
private const val NEARBY = 12.0
