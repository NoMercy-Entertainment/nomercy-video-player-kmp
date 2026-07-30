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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.testing.FakeVideoBackend
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The whole stack, assembled the way an application assembles it.
//
// Every piece below has its own tests against fakes. This is the one that puts
// them together over a real Ktor client, because "each part works" and "the
// parts fit" are different claims and only the second one ships.
class CastVideoFactoryTest {

    private val requests: MutableList<HttpRequestData> = mutableListOf()

    private fun TestScope.eager(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    // Read by index, never iterated.
    //
    // startCast leaves a session poll running on the eager scope, so the engine
    // goes on appending while an assertion reads. An iterator over the live list
    // threw ConcurrentModificationException in the Android host-test run and
    // passed on the JVM one, which is the shape of a flake nobody can reproduce
    // on demand. The list only ever grows, so an index walk cannot miss its footing.
    private fun requestedUrls(): List<String> {
        val urls: MutableList<String> = mutableListOf()
        var index = 0
        while (index < requests.size) {
            urls += requests[index].url.toString()
            index += 1
        }
        return urls
    }

    private fun http(session: String = """{"itemTitle":"Blade Runner 2049"}"""): HttpClient {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = ByteReadChannel(session),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    @Test
    fun oneCallProducesAPluginAPlayerCanRegister() = runTest {
        val plugin: VideoCastPlugin = videoCastPlugin(
            host = "192.168.1.40",
            scope = eager(),
            http = http(),
        )
        val player = NMVideoPlayer(backend = FakeVideoBackend(), video = FakeVideoBackend())
        player.setup(PlayerConfig())

        player.addPlugin(plugin)

        // The web's id, not a native spelling of it. A consumer's
        // getPlugin("cast-sender") has to find this one.
        assertEquals("cast-sender", VideoCastPlugin.id)
    }

    @Test
    fun theAssembledStackReachesTheTelevisionItWasGiven() = runTest {
        // The seam that a fake cannot check: the host and port an application
        // supplied have to end up in the request the client makes.
        val plugin: VideoCastPlugin = videoCastPlugin(
            host = "192.168.1.40",
            port = 7626,
            scope = eager(),
            http = http(),
        )

        plugin.startCast(
            RemoteDevice(id = "dev-a", serviceName = "Living Room-04217", host = "192.168.1.40", port = 7626),
            "https://media.example.test/a.mkv",
        )

        val urls: List<String> = requestedUrls()

        assertTrue(
            urls.any { it.startsWith("http://192.168.1.40:7626/v1") },
            "the stack talked to $urls",
        )
    }

    @Test
    fun whatTheTelevisionSaidReachesTheCastState() = runTest {
        // End to end: a real client, a real controller, a real plugin, and the
        // title from the wire arriving where a chrome would read it.
        val plugin: VideoCastPlugin = videoCastPlugin(
            host = "192.168.1.40",
            scope = eager(),
            http = http(session = """{"itemTitle":"Dune","positionMs":90000}"""),
        )

        plugin.startCast(
            RemoteDevice(id = "dev-a", serviceName = "Living Room", host = "192.168.1.40", port = 7626),
            "https://media.example.test/a.mkv",
        )

        assertEquals("Dune", plugin.castState.value?.itemTitle)
        assertEquals(90_000, plugin.castState.value?.positionMs)
    }

    @Test
    fun theDefaultPortIsTheOneATelevisionListensOn() {
        // Named once so a caller never types it and never gets it wrong.
        assertEquals(7626, DEFAULT_TV_PORT)
    }

    @Test
    fun theDefaultClientToleratesFieldsThisBuildHasNeverSeen() = runTest {
        // The compatibility rule the whole cast surface rests on. A strict
        // reader refuses to control a television that works perfectly well.
        val plugin: VideoCastPlugin = videoCastPlugin(
            host = "192.168.1.40",
            scope = eager(),
            http = http(session = """{"itemTitle":"Dune","addedInALaterBuild":{"nested":true}}"""),
        )

        plugin.startCast(
            RemoteDevice(id = "dev-a", serviceName = "Living Room", host = "192.168.1.40", port = 7626),
            "https://media.example.test/a.mkv",
        )

        assertEquals("Dune", plugin.castState.value?.itemTitle)
    }
}
