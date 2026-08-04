// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.transcode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.plugin.PluginOptionField
import tv.nomercy.player.core.ports.RealtimeChannel
import tv.nomercy.player.core.ports.RealtimeEvent

// Holding the player back until the encoder has caught up.
//
// The server transcodes on demand, segment by segment, and the head of what it
// has written moves forward in real time. Seeking past that head asks for bytes
// that do not exist yet: the engine gets a 404 or a truncated segment and shows
// the viewer a stall it cannot explain. So both `beforeLoad` and `beforeSeek`
// wait here until the server reports the target encoded.
//
// Best-effort by design. The wait gives up at [LiveTranscodingOptions.seekTimeoutMs]
// and lets the seek through rather than holding the player open forever, and
// with no control URL the plugin is inert. A stricter gate belongs to the
// consumer that knows its own server is mandatory — a library that refused to
// play when a transcode service was unreachable would be deciding that for
// every consumer.
public open class LiveTranscodingPlugin(
    opts: LiveTranscodingOptions = LiveTranscodingOptions(),
) : Plugin<LiveTranscodingOptions>() {

    public companion object Manifest : PluginManifest {
        override val id: String = "live-transcoding"

        // Two, matching the web plugin this mirrors.
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    // Held rather than fixed: the two timings below are the ones worth moving
    // while watching an encoder keep up, which means the plugin reads them again.
    private var opts: LiveTranscodingOptions = opts

    override val options: LiveTranscodingOptions get() = opts

    override fun optionFields(): List<PluginOptionField> = listOf(
        PluginOptionField.Number(
            key = "seekTimeoutMs",
            label = "Seek wait for the encoder (ms)",
            value = opts.seekTimeoutMs.toDouble(),
            min = MIN_SEEK_TIMEOUT_MS,
            max = MAX_SEEK_TIMEOUT_MS,
            step = SEEK_TIMEOUT_STEP_MS,
            apply = { chosen -> opts = opts.copy(seekTimeoutMs = chosen.toLong()) },
        ),
        PluginOptionField.Number(
            key = "resumeAheadSeconds",
            label = "Stay ahead by (seconds)",
            value = opts.resumeAheadSeconds,
            min = MIN_RESUME_AHEAD,
            max = MAX_RESUME_AHEAD,
            apply = { chosen -> opts = opts.copy(resumeAheadSeconds = chosen) },
        ),
    )

    private var channel: RealtimeChannel? = null

    private val head: MutableStateFlow<Double> = MutableStateFlow(0.0)

    /** How many seconds the encoder has written, from its own progress reports. */
    public val transcodedTo: StateFlow<Double> = head.asStateFlow()

    private var jobId: String = ""

    override fun use() {
        val url: String = opts.controlUrl ?: return

        channel = websocket(url).also { open: RealtimeChannel ->
            open.on(RealtimeEvent.MESSAGE) { payload: Any? -> onServerMessage(payload) }
        }

        on(CoreEvents.BeforeLoad) { event ->
            head.value = 0.0
            jobId = event.data.item.id
            event.delay { waitFor(0.0) }
        }

        on(CoreEvents.BeforeSeek) { event ->
            val target: Double = event.data.time
            if (target > head.value) event.delay { waitFor(target) }
        }
    }

    override fun dispose() {
        channel = null
        head.value = 0.0
        jobId = ""
    }

    // The channel hands over whatever the transport read, which is a String on
    // the WebSocket default and could be anything on a consumer's own realtime
    // layer. Anything that is not text is not a status report.
    private fun onServerMessage(payload: Any?) {
        val raw: String = payload as? String ?: return
        val message: JsonObject = decode(raw) ?: return
        when (message.text("type")) {
            STARTED -> {
                jobId = message.text("jobId") ?: jobId
                emit(JobStarted, TranscodeJob(jobId, message.text("sourceUrl").orEmpty()))
            }

            PROGRESS -> onProgress(message)
            READY -> emit(JobReadyToPlay, TranscodeJob(jobId))
            COMPLETE -> emit(JobComplete, TranscodeJob(jobId))
            else -> Unit
        }
    }

    private fun onProgress(message: JsonObject) {
        val seconds: Double = message.number("transcodedSeconds") ?: return
        head.value = seconds
        emit(
            JobProgress,
            TranscodeProgress(
                jobId = message.text("jobId") ?: jobId,
                transcodedSeconds = seconds,
                totalSeconds = message.number("totalSeconds"),
                variantsReady = message.strings("variantsReady"),
            ),
        )
    }

    // Unreadable traffic is dropped rather than thrown.
    //
    // This socket belongs to somebody else's server, and a transcoder that
    // starts sending a field this version has never seen must not take the
    // player down with it.
    private fun decode(raw: String): JsonObject? = try {
        lenient.parseToJsonElement(raw) as? JsonObject
    } catch (malformed: SerializationException) {
        logger.debug("dropped an unreadable transcoder message", malformed)
        null
    }

    // Waits by watching the head this plugin already keeps, which is the same
    // value the progress event carries — so a consumer drawing a "preparing"
    // bar and the gate holding the seek cannot disagree about how far along the
    // encoder is.
    private suspend fun waitFor(target: Double) {
        if (target <= head.value) return
        withTimeoutOrNull(opts.seekTimeoutMs) {
            head.first { written: Double -> target <= written }
        }
    }
}

private val lenient: Json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

private fun JsonObject.strings(key: String): List<String> =
    this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

private const val STARTED = "started"
private const val PROGRESS = "progress"
private const val READY = "ready-to-play"
private const val COMPLETE = "complete"

/** Emitted when the server accepts a job for the item being loaded. */
public val JobStarted: EventKey<TranscodeJob> = EventKey("job:started")

/** Emitted on every progress report, carrying the encoder's write head. */
public val JobProgress: EventKey<TranscodeProgress> = EventKey("job:progress")

/** Emitted when the server says enough exists to start playing. */
public val JobReadyToPlay: EventKey<TranscodeJob> = EventKey("job:ready-to-play")

/** Emitted when the server has finished encoding the item. */
public val JobComplete: EventKey<TranscodeJob> = EventKey("job:complete")

/** A transcoding job, and where its source came from when the server said. */
public data class TranscodeJob(val jobId: String, val sourceUrl: String = "")

/** How far the encoder has written, and which renditions exist so far. */
public data class TranscodeProgress(
    val jobId: String,
    val transcodedSeconds: Double,
    val totalSeconds: Double? = null,
    val variantsReady: List<String> = emptyList(),
)

// What an editor offers. Below half a second a seek gives up before an encoder
// on a busy box has answered; past thirty the viewer has concluded it is broken.
private const val MIN_SEEK_TIMEOUT_MS: Double = 500.0
private const val MAX_SEEK_TIMEOUT_MS: Double = 30_000.0
private const val SEEK_TIMEOUT_STEP_MS: Double = 500.0
private const val MIN_RESUME_AHEAD: Double = 1.0
private const val MAX_RESUME_AHEAD: Double = 120.0
