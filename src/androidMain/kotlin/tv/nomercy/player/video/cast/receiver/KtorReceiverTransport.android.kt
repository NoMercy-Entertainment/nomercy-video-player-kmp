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
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
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
    private val sessions = CopyOnWriteArrayList<DefaultWebSocketServerSession>()

    // Its own scope rather than borrowing a session's — a session's
    // coroutine ends the moment that socket closes, and a broadcast fired
    // between two connections (or to the nine still open when a tenth
    // drops) must not ride on one particular sender's lifetime.
    private var broadcastScope: CoroutineScope? = null

    // Where this receiver actually bound, once [start] has run — TvControlServer
    // shares the same fallback range, so both servers finding it worth logging
    // when they land somewhere other than [port] is the same call this makes.
    public var boundPort: Int = port
        private set

    override fun start(commandHandler: (senderId: String, command: ReceiverCommand) -> ReceiverOutcome) {
        if (engine != null) return
        boundPort = findFreePort(port)
        broadcastScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        engine = embeddedServer(Netty, port = boundPort, host = "0.0.0.0") {
            install(WebSockets)
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                webSocket("/ws/receiver/{senderId}") {
                    val senderId = call.parameters["senderId"]
                    if (senderId.isNullOrBlank() || !authorize(senderId)) {
                        close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                        return@webSocket
                    }
                    sessions += this
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val command = runCatching { Json.decodeFromString<WireCommand>(frame.readText()) }
                                .getOrNull()
                                ?.toDomain()
                                ?: continue
                            val outcome = commandHandler(senderId, command)
                            // Guarded like broadcast() below, against a dead socket.
                            runCatching {
                                send(Frame.Text(Json.encodeToString(WireOutcome.serializer(), WireOutcome.from(outcome))))
                            }
                        }
                    } finally {
                        sessions -= this
                        disconnectHandlers.value.forEach { it(senderId) }
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    override fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        engine = null
        broadcastScope?.cancel()
        broadcastScope = null
        sessions.clear()
    }

    override fun onDisconnect(handler: (senderId: String) -> Unit) {
        disconnectHandlers.update { it + handler }
    }

    override fun broadcast(event: ReceiverStateEvent) {
        val scope = broadcastScope ?: return
        val frame = Frame.Text(Json.encodeToString(WireStateEvent.serializer(), WireStateEvent.from(event)))
        // A dead socket refusing a frame is that sender's problem, already
        // reported through onDisconnect once incoming{} unwinds — it must
        // not stop the snapshot reaching everyone else still connected.
        sessions.forEach { session -> scope.launch { runCatching { session.send(frame) } } }
    }

    // The same fallback TvControlServer's own `findFreePort` runs: this
    // receiver and that server can end up sharing a process (P22b.3's
    // still-open app wiring runs both side by side), so a fixed port with no
    // fallback would make the second one to start simply fail to bind.
    private fun findFreePort(preferred: Int): Int {
        if (isPortFree(preferred)) return preferred
        for (candidate in (preferred + 1)..PORT_RANGE_END) {
            if (isPortFree(candidate)) return candidate
        }
        return preferred
    }

    private fun isPortFree(candidate: Int): Boolean = runCatching {
        ServerSocket(candidate).use { true }
    }.getOrDefault(false)

    public companion object {
        public const val DEFAULT_PORT: Int = 7626
        private const val PORT_RANGE_END: Int = 7699
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

@Serializable
private data class WireStateEvent(
    val playbackState: String,
    val positionMs: Long,
    val durationMs: Long,
    val itemId: String? = null,
    val itemTitle: String? = null,
    val audioTrackId: String? = null,
    val subtitleTrackId: String? = null,
    val qualityLabel: String,
    val volumeLevel: Int,
    val muted: Boolean,
    val playlistLength: Int,
    val playlistActiveIndex: Int,
) {
    companion object {
        fun from(event: ReceiverStateEvent): WireStateEvent = WireStateEvent(
            playbackState = event.playbackState,
            positionMs = event.positionMs,
            durationMs = event.durationMs,
            itemId = event.itemId,
            itemTitle = event.itemTitle,
            audioTrackId = event.audioTrackId,
            subtitleTrackId = event.subtitleTrackId,
            qualityLabel = event.qualityLabel,
            volumeLevel = event.volumeLevel,
            muted = event.muted,
            playlistLength = event.playlistLength,
            playlistActiveIndex = event.playlistActiveIndex,
        )
    }
}
