// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The real transport, without a television.
//
// The mock engine hands back an actual byte channel, so the stream reader takes
// the same path here that it takes against a set across the house — which is the
// only reason a test can say anything about it at all.
class KtorTvControlClientTest {

    private val requests: MutableList<HttpRequestData> = mutableListOf()

    private fun clientFor(
        token: String? = "tok-1",
        respondWith: (HttpRequestData) -> Pair<String, String> = { "{}" to ContentType.Application.Json.toString() },
        status: HttpStatusCode = HttpStatusCode.OK,
    ): KtorTvControlClient {
        val engine = MockEngine { request ->
            requests += request
            val (body, type) = respondWith(request)
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, type),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return KtorTvControlClient("192.168.1.40", 7626, http, token = { token })
    }

    // Joined explicitly rather than written as an indented literal. A raw string
    // makes the blank lines — which are what closes a frame — invisible in the
    // source and easy to get wrong, and this is a protocol where a missing blank
    // line means the frame never arrives.
    private fun stream(vararg lines: String): Pair<String, String> =
        lines.joinToString(separator = "\n", postfix = "\n") to "text/event-stream"

    private fun bodyOf(request: HttpRequestData): String =
        (request.body as io.ktor.http.content.TextContent).text

    @Test
    fun everyRequestCarriesTheTokenAsItIsAtThatMoment() = runTest {
        // Read per request rather than captured once. A token expires, and a
        // client holding the one it was built with starts failing halfway
        // through an evening with no way back short of being rebuilt.
        val client = clientFor(token = "tok-1")

        client.play()

        assertEquals("Bearer tok-1", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun withNoTokenNoHeaderIsSentAtAll() = runTest {
        // An empty bearer turns "not signed in yet" into a malformed credential,
        // and those need different messages to a viewer.
        val client = clientFor(token = null)

        client.getServer()

        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun aSeekIsSentAsMillisecondsInTheBody() = runTest {
        val client = clientFor()

        client.seek(90_000)

        assertTrue(bodyOf(requests.single()).contains("\"positionMs\":90000"))
    }

    @Test
    fun aVolumeChangeSendsOnlyWhatChanged() = runTest {
        // A body naming every field would have a mute button overwrite a level
        // the viewer had just set at the television itself.
        val client = clientFor()

        client.setVolume(muted = true)

        val body: String = bodyOf(requests.single())
        assertTrue(body.contains("\"muted\":true"))
        assertFalse(body.contains("level"), "a level was sent for a mute: $body")
    }

    @Test
    fun turningSubtitlesOffSendsANullRatherThanOmittingTheField() = runTest {
        // Omitting means unchanged. This is the way they are turned off, so the
        // null has to be on the wire.
        val client = clientFor()

        client.setSubtitleTrack(null)

        assertTrue(bodyOf(requests.single()).contains("\"trackId\":null"))
    }

    @Test
    fun aRefusedLaunchIsReportedRatherThanAssumed() = runTest {
        val client = clientFor(status = HttpStatusCode.Conflict)

        assertFalse(client.launch("https://media.example.test/a.mkv"))
    }

    @Test
    fun anAcceptedLaunchSaysSo() = runTest {
        val client = clientFor(status = HttpStatusCode.Accepted)

        assertTrue(client.launch("https://media.example.test/a.mkv"))
    }

    @Test
    fun theSessionDecodesFromWhatTheSetSends() = runTest {
        val client = clientFor(
            respondWith = {
                """{"sessionId":"s1","itemTitle":"Blade Runner 2049","positionMs":42000}""" to
                    ContentType.Application.Json.toString()
            },
        )

        val state: RemotePlayerState = client.getSession()

        assertEquals("Blade Runner 2049", state.itemTitle)
        assertEquals(42_000, state.positionMs)
    }

    @Test
    fun theStreamIsReadFrameByFrameRatherThanWaitedForWhole() = runTest {
        // A television emits frames for as long as it is playing. Anything that
        // waits for a complete body waits until the set is switched off.
        val client = clientFor(
            respondWith = {
                stream(
                    "event:state",
                    """data:{"itemTitle":"First"}""",
                    "",
                    "event:transport",
                    """data:{"transport":"paused"}""",
                    "",
                )
            },
        )

        val events: List<RemoteEvent> = client.events().take(2).toList()

        assertEquals("First", (events[0] as RemoteEvent.State).state.itemTitle)
        assertEquals(RemoteEvent.Transport("paused"), events[1])
    }

    @Test
    fun aMalformedFrameIsSkippedAndTheStreamCarriesOn() = runTest {
        // One bad frame from a device across the house must not end the
        // subscription — the set carries on playing and the phone would stop
        // listening.
        val client = clientFor(
            respondWith = {
                stream(
                    "data:not json at all",
                    "",
                    "event:transport",
                    """data:{"transport":"playing"}""",
                    "",
                )
            },
        )

        val events: List<RemoteEvent> = client.events().take(1).toList()

        assertEquals(RemoteEvent.Transport("playing"), events.single())
    }

    @Test
    fun theStreamIsAskedForOnTheEventsEndpointAndCarriesTheToken() = runTest {
        // Taking a frame, because a flow nobody collects opens no connection —
        // and a test that collected none would assert against an empty list and
        // pass whatever the client did.
        val client = clientFor(
            respondWith = { stream("event:transport", """data:{"transport":"playing"}""", "") },
        )

        client.events().take(1).toList()

        assertTrue(
            requests.single().url.encodedPath.endsWith("/v1/events"),
            "the stream was opened against ${requests.single().url}",
        )
        assertEquals("Bearer tok-1", requests.single().headers[HttpHeaders.Authorization])
    }
}
