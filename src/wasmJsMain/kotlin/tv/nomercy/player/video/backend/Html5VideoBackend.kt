// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package tv.nomercy.player.video.backend

import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.CanonicalBackendEvent
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.StringEventBus
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.core.ports.TimeRange
import tv.nomercy.player.core.ports.VideoBackend

@JsFun("(parent, child) => { parent.appendChild(child); }")
private external fun jsAppendChild(parent: JsAny, child: JsAny)

/**
 * A `<video>` element driven directly, as a [VideoBackend].
 *
 * This IS the web trio's own engine: [nomercy-video-player]'s `Html5VideoBackend`
 * (`packages/nomercy-video-player/src/adapters/video-backend/html5.ts`) wraps the
 * exact same `HTMLVideoElement`, and this port drives it the same way — through
 * `src` assignment and the element's own demux+decode pipeline, which on a Cast
 * receiver's Chromium build handles progressive MP4/WebM and native HLS the same
 * as any other Chromium tab. The web reference layers `hls.js` on top for browsers
 * without native HLS (`canPlayType` false) to demux HLS into `MediaSource`
 * manually; that layer is NOT ported here. See [qualityLevels] and
 * [bandwidthEstimate] for exactly what that costs and why it is an honest gap
 * rather than a silent one — a hand-written re-implementation of hls.js's ABR,
 * HDR-aware level capping and three-tier error recovery (~1000 lines in the web
 * source) is real, sizeable follow-up work, not something this slice fakes.
 *
 * Events are wired directly: the canonical vocabulary in [CanonicalBackendEvent]
 * IS the `<video>` element's own event names, verbatim — this backend needs no
 * mapper the way ExoPlayer's state-integer surface needs [tv.nomercy.player.video.backend.ExoEventMapper].
 */
@Suppress("TooManyFunctions")
public class Html5VideoBackend internal constructor(
    private val element: JsVideoElement,
) : VideoBackend {

    public constructor() : this(jsCreateVideoElement())

    /**
     * Mounts the element this backend drives into [parent].
     *
     * A method rather than exposing the element itself: [JsVideoElement] is
     * `internal` (an interop detail, not part of the [VideoBackend] contract a
     * consumer outside this module should depend on), so a cross-module
     * consumer — cast-web's `Html5PlaybackShellState` — asks this backend to
     * place its own surface rather than reaching in to do it directly.
     */
    public fun mountInto(parent: JsAny) {
        jsAppendChild(parent, element)
    }

    private val bus = StringEventBus()
    private var released: Boolean = false
    private var loadStarted: Boolean = false
    private var hadError: Boolean = false
    private var lastMuteVolume: Double = 1.0

    private val listeners: MutableList<Pair<String, (JsAny) -> Unit>> = mutableListOf()

    init {
        wireElementEvents()
    }

    // ---- loading -------------------------------------------------------

    override suspend fun load(url: String, opts: LoadOptions) {
        hadError = false
        loadStarted = true
        bus.emit(CanonicalBackendEvent.LOAD_START)

        clearTextTrackCues()
        jsResetVideoElement(element)

        element.src = url.toJsString()
        try {
            element.load()
        } catch (@Suppress("SwallowedException") ignored: Throwable) {
            // Defensive, matching the web reference: some engines refuse a
            // load() call before the element has settled from the reset above.
        }

        if (opts.startPositionMs > 0L) {
            element.currentTime = opts.startPositionMs / MILLIS_PER_SECOND
        }

        if (opts.autoplay) play()
    }

    override suspend fun play() {
        element.play()
    }

    override fun pause() {
        element.pause()
    }

    override fun stop() {
        element.pause()
        element.currentTime = 0.0
    }

    override fun release() {
        if (released) return
        released = true
        jsResetVideoElement(element)
        for ((type, listener) in listeners) element.removeEventListener(type.toJsString(), listener)
        listeners.clear()
        bus.clear()
    }

    // ---- the playhead ---------------------------------------------------

    override fun currentTime(): Double = element.currentTime

    override fun currentTime(seconds: Double) {
        element.currentTime = seconds
    }

    override fun duration(): Double = element.duration.takeIf { it.isFinite() } ?: 0.0

    override fun volume(): Float = element.volume.toFloat()

    override fun volume(value: Float) {
        element.volume = value.toDouble().coerceIn(0.0, 1.0)
    }

    override fun mute() {
        lastMuteVolume = element.volume
        element.muted = true
    }

    override fun unmute() {
        element.muted = false
    }

    override fun playbackRate(): Double = element.playbackRate

    override fun playbackRate(rate: Double) {
        element.playbackRate = rate
    }

    /**
     * Where buffered data reaches, walked the same way the web reference's own
     * `buffered()` override does — the interface default is not enough here,
     * because [bufferedRanges] is the honest source and this walk is written
     * once rather than trusting the shared frontier walk to know a browser's
     * `TimeRanges` shape.
     */
    override fun buffered(): Double {
        val ranges: List<TimeRange> = bufferedRanges()
        val position: Double = currentTime()
        val covering: TimeRange? = ranges.firstOrNull { position in it }
        if (covering != null) return covering.end
        val last: TimeRange? = ranges.lastOrNull()
        return when {
            last == null -> position
            position > last.end -> last.end
            else -> position
        }
    }

    override fun bufferedRanges(): List<TimeRange> = element.buffered.toRangeList()

    override fun seekableRanges(): List<TimeRange> = element.seekable.toRangeList()

    private fun JsTimeRanges.toRangeList(): List<TimeRange> =
        (0 until length).map { index -> TimeRange(jsTimeRangeStart(this, index), jsTimeRangeEnd(this, index)) }

    /**
     * No hls.js in this slice, so there is no EWMA throughput estimator to read
     * — see the class doc. Zero is [tv.nomercy.player.core.ports.MediaBackend]'s
     * own honest default for "this engine does not measure", not a stand-in.
     */
    override fun bandwidthEstimate(): Int = 0

    override fun state(): BackendState = when {
        released -> BackendState.IDLE
        hadError -> BackendState.ERROR
        !loadStarted -> BackendState.IDLE
        !element.paused && !element.ended -> BackendState.PLAYING
        element.paused && element.readyState >= READY_STATE_HAVE_CURRENT_DATA && !element.ended -> BackendState.PAUSED
        element.readyState >= READY_STATE_HAVE_METADATA -> BackendState.READY
        else -> BackendState.LOADING
    }

    // ---- tracks -----------------------------------------------------------

    /**
     * `HTMLMediaElement.audioTracks` is a non-standard Chrome/Edge extension —
     * absent on Firefox and on browsers that expose only `video.audioTracks ===
     * undefined`. A source with a single audio track (the overwhelming majority
     * on a NoMercy library) reports that one track; a genuinely multi-audio HLS
     * source without hls.js's own `audioTracks` list (see the class doc) reports
     * whatever the browser's demuxer happened to expose, which may be one track
     * even when the container carries more.
     */
    override fun audioTracks(): List<AudioTrack> {
        val list: JsAudioTrackList = element.audioTracks ?: return emptyList()
        return (0 until list.length).map { index ->
            val track: JsAudioTrack = jsAudioTrackAt(list, index)
            AudioTrack(
                id = track.id.toString().ifEmpty { "audio-$index" },
                language = track.language?.toString()?.ifEmpty { null } ?: "und",
                label = track.label?.toString()?.ifEmpty { null } ?: "Track ${index + 1}",
            )
        }
    }

    override fun audioTrack(): AudioTrack? {
        val list: JsAudioTrackList = element.audioTracks ?: return null
        return (0 until list.length).firstOrNull { index -> jsAudioTrackAt(list, index).enabled }
            ?.let { index -> audioTracks().getOrNull(index) }
    }

    override fun audioTrack(track: AudioTrack) {
        val list: JsAudioTrackList = element.audioTracks ?: return
        val tracks: List<AudioTrack> = audioTracks()
        val wanted: Int = tracks.indexOf(track)
        for (index in 0 until list.length) jsAudioTrackAt(list, index).enabled = index == wanted
    }

    override fun subtitleTracks(): List<SubtitleTrack> {
        val list: JsTextTrackList = element.textTracks
        return (0 until list.length).mapNotNull { index ->
            val track: JsTextTrack = jsTextTrackAt(list, index)
            val kind: String = track.kind.toString()
            if (kind != "subtitles" && kind != "captions") return@mapNotNull null
            SubtitleTrack(
                id = "subtitle-$index",
                language = track.language?.toString()?.ifEmpty { null } ?: "und",
                label = track.label?.toString()?.ifEmpty { null } ?: "Subtitles ${index + 1}",
                forced = false,
            )
        }
    }

    override fun subtitleTrack(): SubtitleTrack? {
        val list: JsTextTrackList = element.textTracks
        val showing: Int = (0 until list.length).indexOfFirst { index ->
            val kind: String = jsTextTrackAt(list, index).kind.toString()
            (kind == "subtitles" || kind == "captions") && jsTextTrackAt(list, index).mode.toString() == "showing"
        }
        return if (showing < 0) null else subtitleTracks().getOrNull(subtitleIndexOf(list, showing))
    }

    // `subtitleTracks()` skips non-subtitle/caption entries, so the Nth
    // matching element in `textTracks` is not necessarily the Nth entry this
    // backend reports — this converts a raw `textTracks` index into that
    // filtered position.
    private fun subtitleIndexOf(list: JsTextTrackList, rawIndex: Int): Int {
        var seen = -1
        for (index in 0..rawIndex) {
            val kind: String = jsTextTrackAt(list, index).kind.toString()
            if (kind == "subtitles" || kind == "captions") seen += 1
        }
        return seen
    }

    override fun subtitleTrack(track: SubtitleTrack?) {
        val list: JsTextTrackList = element.textTracks
        if (track == null) {
            for (index in 0 until list.length) {
                val jsTrack: JsTextTrack = jsTextTrackAt(list, index)
                val kind: String = jsTrack.kind.toString()
                if (kind == "subtitles" || kind == "captions") jsTrack.mode = "disabled".toJsString()
            }
            return
        }

        val wanted: Int = subtitleTracks().indexOf(track)
        var seen = -1
        for (index in 0 until list.length) {
            val jsTrack: JsTextTrack = jsTextTrackAt(list, index)
            val kind: String = jsTrack.kind.toString()
            if (kind != "subtitles" && kind != "captions") continue
            seen += 1
            jsTrack.mode = if (seen == wanted) "showing".toJsString() else "disabled".toJsString()
        }
    }

    /**
     * No renditions, honestly. Without hls.js this backend never parses an
     * HLS/DASH manifest itself — the browser's own native HLS engine (Safari,
     * and a Chromium build with proprietary codecs) demuxes and adapts behind a
     * closed door exactly the way libVLC and AVFoundation do on the other two
     * platforms, and reports nothing back. A menu built from this list has
     * nothing to show; that is the true state of what this engine can say, not
     * a placeholder for a future call that was skipped.
     */
    override fun qualityLevels(): List<QualityLevel> = emptyList()

    override fun quality(): QualityLevel? = null

    // Auto is the only state a source-less engine has, so pinning a specific
    // level is a no-op — the same shape MediaBackend's own bandwidthEstimate()
    // default takes for "cannot say" rather than throwing.
    override fun quality(level: QualityLevel?) {}

    // A `<video>` element lays itself out in the DOM; the ladder decision this
    // exists for on mpv and libVLC has nothing to act on without hls.js's
    // `autoLevelCapping` (see qualityLevels()), so this stays the interface's
    // own no-op default rather than pretending a call here does something.
    override fun surfaceSize(widthPx: Int, heightPx: Int) {}

    // ---- events -------------------------------------------------------

    override fun on(event: String, fn: (Any?) -> Unit): Unit = bus.on(event, fn)

    override fun off(event: String, fn: (Any?) -> Unit): Unit = bus.off(event, fn)

    private fun track(type: String, emit: String = type, payload: (JsAny) -> Any? = { null }) {
        val listener: (JsAny) -> Unit = { event -> bus.emit(emit, payload(event)) }
        element.addEventListener(type.toJsString(), listener)
        listeners.add(type to listener)
    }

    private fun wireElementEvents() {
        track(CanonicalBackendEvent.LOAD_START)
        track(CanonicalBackendEvent.LOADED_METADATA)
        track(CanonicalBackendEvent.CAN_PLAY)
        track(CanonicalBackendEvent.PLAY)
        track(CanonicalBackendEvent.PLAYING)
        track(CanonicalBackendEvent.TIME_UPDATE) { currentTime() }
        track(CanonicalBackendEvent.DURATION_CHANGE)
        track(CanonicalBackendEvent.WAITING)
        track(CanonicalBackendEvent.STALLED)
        track(CanonicalBackendEvent.PAUSE)
        track("error", emit = CanonicalBackendEvent.ERROR) {
            hadError = true
            element.error?.message?.toString()
        }
        track(CanonicalBackendEvent.ENDED)
    }

    private fun clearTextTrackCues() {
        // The web reference clears every TextTrack's cues before a new load —
        // the browser's textTracks list survives `src` changes, so without this
        // a second load() sees the union of every previous item's subtitles.
        // `TextTrack.cues`/`removeCue` are not modelled here (no consumer of
        // this backend reads them directly; cues arrive through subtitleTrack()
        // selection + the sidecar/SubtitleOverlayPlugin path), so the honest
        // equivalent available through this element's OWN surface is disabling
        // every track, which stops it painting stale cues natively.
        val list: JsTextTrackList = element.textTracks
        for (index in 0 until list.length) jsTextTrackAt(list, index).mode = "disabled".toJsString()
    }

    private companion object {
        const val MILLIS_PER_SECOND: Double = 1000.0
        const val READY_STATE_HAVE_METADATA: Int = 1
        const val READY_STATE_HAVE_CURRENT_DATA: Int = 2
    }
}
