// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The television's control protocol, spoken over ordinary HTTP.
//
// Portable on purpose: the same file serves a phone, a desktop and an iPad, with
// only the engine underneath differing. That is possible because the wire is
// REST and an event stream rather than anything platform-specific, and it is why
// the library owns this transport instead of asking an application for one.
//
// The token is read per request rather than captured once. It expires, and a
// client holding the one it was built with would start failing halfway through
// an evening with no way to recover short of being rebuilt.
public open class KtorTvControlClient(
    private val host: String,
    private val port: Int,
    private val http: HttpClient,
    private val token: suspend () -> String? = { null },
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TvControlClient {

    private val base: String get() = "http://$host:$port/v1"

    override suspend fun getServer(): RemoteServer = get("$base/info")

    override suspend fun getSession(): RemotePlayerState = get("$base/session")

    override suspend fun getPlaylist(): RemotePlaylist = get("$base/session/playlist")

    override suspend fun launch(url: String, autoplay: Boolean, source: String): Boolean {
        val response = send(
            "$base/launch",
            buildJsonObject {
                put("url", url)
                put("autoplay", autoplay)
                put("source", source)
            },
        )
        return response.status.value in ACCEPTED
    }

    override suspend fun play() {
        send("$base/session/transport/play")
    }

    override suspend fun pause() {
        send("$base/session/transport/pause")
    }

    override suspend fun stop() {
        send("$base/session/transport/stop")
    }

    override suspend fun next() {
        send("$base/session/transport/next")
    }

    override suspend fun previous() {
        send("$base/session/transport/previous")
    }

    override suspend fun seek(positionMs: Long) {
        send("$base/session/transport/seek", buildJsonObject { put("positionMs", positionMs) })
    }

    // Only what changed is sent. A body naming every field would make a mute
    // button overwrite a level the viewer just set from the television itself.
    override suspend fun setVolume(level: Double?, delta: Double?, muted: Boolean?) {
        val body: JsonObject = buildJsonObject {
            level?.let { put("level", it) }
            delta?.let { put("delta", it) }
            muted?.let { put("muted", it) }
        }
        send("$base/session/volume", body, HttpMethod.Patch)
    }

    override suspend fun setAudioTrack(trackId: String) {
        send("$base/session/audio", buildJsonObject { put("trackId", trackId) })
    }

    // A null id is sent as a null rather than omitted, because omitting it means
    // "unchanged" and this is the way subtitles are turned off.
    override suspend fun setSubtitleTrack(trackId: String?) {
        val body: JsonObject = buildJsonObject {
            put("trackId", trackId?.let(::JsonPrimitive) ?: JsonPrimitive(null as String?))
        }
        send("$base/session/subtitle", body)
    }

    override suspend fun setQuality(level: RemoteQualityLevel) {
        send("$base/session/quality", buildJsonObject { put("level", level.wireName()) })
    }

    override suspend fun setPlaylistActive(index: Int) {
        send("$base/session/playlist/active", buildJsonObject { put("index", index) })
    }

    // The stream, line by line.
    //
    // Read as lines rather than handed to a parser wholesale because it never
    // ends: a television emits frames for as long as it is playing, and anything
    // waiting for a complete body waits until the set is switched off.
    override fun events(): Flow<RemoteEvent> = flow {
        val bearer: String? = token()

        http.prepareGet("$base/events") {
            headers { authorize(bearer) }
        }.execute { response ->
            val channel: ByteReadChannel = response.bodyAsChannel()
            val accumulator = SseAccumulator()

            var line: String? = channel.readUTF8Line()
            while (line != null) {
                accumulator.feed(line)
                    ?.let { frame -> decodeRemoteEvent(frame.first, frame.second, json) }
                    ?.let { event -> emit(event) }

                line = channel.readUTF8Line()
            }
        }
    }

    // Resolved before the request is built, because a request builder is not a
    // suspending context and the token is fetched rather than held.
    private suspend inline fun <reified T> get(url: String): T {
        val bearer: String? = token()
        return http.request(url) { headers { authorize(bearer) } }.body()
    }

    private suspend fun send(
        url: String,
        body: JsonObject? = null,
        method: HttpMethod = HttpMethod.Post,
    ): io.ktor.client.statement.HttpResponse {
        val bearer: String? = token()

        return http.request(url) {
            this.method = method
            headers { authorize(bearer) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), body))
            }
        }
    }

    private companion object {
        val ACCEPTED = 200..299
    }
}

// Blank means unauthenticated, which the handshake endpoint accepts and the rest
// do not. Sending an empty bearer instead turns "not signed in yet" into a
// malformed credential, and those two need different messages to a viewer.
private fun io.ktor.http.HeadersBuilder.authorize(bearer: String?) {
    if (!bearer.isNullOrBlank()) append("Authorization", "Bearer $bearer")
}

// The lowercase word the television uses, rather than the enum's own name.
private fun RemoteQualityLevel.wireName(): String = name.lowercase()
