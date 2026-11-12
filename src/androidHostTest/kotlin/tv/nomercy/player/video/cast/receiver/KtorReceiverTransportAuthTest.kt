// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class KtorReceiverTransportAuthTest {

    private val client = OkHttpClient()
    private var transport: KtorReceiverTransport? = null
    private val handled = CopyOnWriteArrayList<ReceiverCommand>()
    private val handledLatch = CountDownLatch(1)

    @AfterTest
    fun tearDown() {
        transport?.stop()
        client.dispatcher.executorService.shutdown()
    }

    private fun startWith(build: () -> KtorReceiverTransport): KtorReceiverTransport {
        val started = build()
        started.start { _, command ->
            handled += command
            handledLatch.countDown()
            ReceiverOutcome.Accepted
        }
        transport = started
        return started
    }

    private class Probe : WebSocketListener() {
        val closed = CountDownLatch(1)
        var code: Int = -1

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            this.code = code
            closed.countDown()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this.code = code
            closed.countDown()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            closed.countDown()
        }
    }

    private fun connect(port: Int, bearer: String?, probe: Probe): WebSocket {
        val request = Request.Builder().url("ws://127.0.0.1:$port/ws/receiver/x").apply {
            if (bearer != null) header("Authorization", "Bearer $bearer")
        }.build()
        return client.newWebSocket(request, probe)
    }

    @Test
    fun defaultPolicyRefusesASenderWithoutACredential() {
        val receiver = startWith { KtorReceiverTransport(port = 17_626) }
        val probe = Probe()

        val socket = connect(receiver.boundPort, null, probe)
        socket.send("""{"type":"launch","url":"https://example.invalid/a.m3u8"}""")

        assertTrue(probe.closed.await(5, TimeUnit.SECONDS), "socket must close")
        assertEquals(1008, probe.code)
        assertTrue(handled.isEmpty(), "command handler must never run")
    }

    @Test
    fun aPolicyThatAcceptsTheBearerLetsALaunchThrough() {
        val receiver = startWith {
            KtorReceiverTransport(
                port = 17_650,
                policy = ReceiverAuthPolicy { it.bearer == "test-token" },
            )
        }
        val probe = Probe()

        val socket = connect(receiver.boundPort, "test-token", probe)
        socket.send("""{"type":"launch","url":"https://example.invalid/a.m3u8"}""")

        assertTrue(handledLatch.await(5, TimeUnit.SECONDS), "handler must receive the frame")
        assertEquals(ReceiverCommand.Launch("https://example.invalid/a.m3u8", true), handled.single())
        socket.close(1000, null)
    }
}
