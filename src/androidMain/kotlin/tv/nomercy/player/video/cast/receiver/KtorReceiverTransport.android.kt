// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tv.nomercy.player.video.cast.RemoteQualityLevel

// P22b Task 3 — the WebSocket half of ReceiverTransport, for senders that
// already hold a NoMercy connection (the KMP sender's TvControlClient, this
// app's own remote UI). A third-party Cast sender never reaches this class;
// that is CastReceiverTransport's audience, not this one.
//
// This is the wire this library owns: a JSON envelope per frame, one
// connection per sender, the sender id taken from the WebSocket path so a
// reconnect after a dropped socket is a new sender as far as ReceiverSession
// is concerned — exactly like a phone that force-quit and relaunched.
//
// What it deliberately does NOT do: authenticate. `nomercy-app-kmp`'s own
// TvControlServer guards this port with offline JWT verification against
// cached Keycloak JWKS — that policy belongs to the application, the same
// way SystemTransport's OS integration is injected rather than owned here.
// [authorize] is that seam; the default accepts everyone, which is correct
// for a fake in a test and wrong for anything a real network can reach.
public class KtorReceiverTransport(
    private val port: Int = DEFAULT_PORT,
    private val authorize: (senderId: String) -> Boolean = { true },
) : ReceiverTransport {

    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val disconnectHandlers = MutableStateFlow<List<(String) -> Unit>>(emptyList())

    override fun start(commandHandler: (senderId: String, command: ReceiverCommand) -> ReceiverOutcome) {
        if (engine != null) return
        engine = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(WebSockets)
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                webSocket("/ws/receiver/{senderId}") {
                    val senderId = call.parameters["senderId"]
                    if (senderId.isNullOrBlank() || !authorize(senderId)) {
                        close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                        return@webSocket
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val command = runCatching { Json.decodeFromString<WireCommand>(frame.readText()) }
                                .getOrNull()
                                ?.toDomain()
                                ?: continue
                            val outcome = commandHandler(senderId, command)
                            send(Frame.Text(Json.encodeToString(WireOutcome.serializer(), WireOutcome.from(outcome))))
                        }
                    } finally {
                        disconnectHandlers.value.forEach { it(senderId) }
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    override fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        engine = null
    }

    override fun onDisconnect(handler: (senderId: String) -> Unit) {
        disconnectHandlers.update { it + handler }
    }

    public companion object {
        public const val DEFAULT_PORT: Int = 7626
    }
}

// The wire shape, kept apart from ReceiverCommand on purpose — the domain
// type has no transport, and a JSON field renamed for the wire must not
// force every ReceiverSession caller to change with it.
@Serializable
private data class WireCommand(
    val type: String,
    val url: String? = null,
    val autoplay: Boolean = true,
    val positionMs: Long? = null,
    val level: Double? = null,
    val delta: Double? = null,
    val muted: Boolean? = null,
    val trackId: String? = null,
    val subtitlesEnabled: Boolean = true,
    val qualityLevel: String? = null,
) {
    fun toDomain(): ReceiverCommand? = when (type) {
        "launch" -> ReceiverCommand.Launch(url ?: "", autoplay)
        "play" -> ReceiverCommand.Play
        "pause" -> ReceiverCommand.Pause
        "stop" -> ReceiverCommand.Stop
        "next" -> ReceiverCommand.Next
        "previous" -> ReceiverCommand.Previous
        "seek" -> ReceiverCommand.Seek(positionMs ?: 0L)
        "volume" -> ReceiverCommand.SetVolume(level, delta, muted)
        "audio" -> ReceiverCommand.SetAudioTrack(trackId ?: "")
        "subtitle" -> ReceiverCommand.SetSubtitleTrack(if (subtitlesEnabled) trackId else null)
        "quality" -> ReceiverCommand.SetQuality(
            RemoteQualityLevel.entries.firstOrNull { it.name.equals(qualityLevel, ignoreCase = true) }
                ?: RemoteQualityLevel.AUTO,
        )
        // An unrecognised type is a protocol version mismatch, not a
        // transport-level failure the connection needs to survive — dropped
        // below rather than guessed at as some other command.
        else -> null
    }
}

@Serializable
private data class WireOutcome(
    val accepted: Boolean,
    @SerialName("reason") val refusalReason: String? = null,
) {
    companion object {
        fun from(outcome: ReceiverOutcome): WireOutcome = when (outcome) {
            is ReceiverOutcome.Accepted -> WireOutcome(accepted = true)
            is ReceiverOutcome.Refused -> WireOutcome(accepted = false, refusalReason = outcome.reason)
        }
    }
}
