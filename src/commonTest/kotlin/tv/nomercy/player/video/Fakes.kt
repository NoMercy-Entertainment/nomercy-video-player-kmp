// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.CanonicalBackendEvent
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.core.ports.VideoBackend

internal data class VideoItem(
    override val id: String,
    override val url: String = "https://example.test/$id.m3u8",
    override val title: String? = null,
) : PlaylistItem

// An engine that reports tracks and answers on its own event stream.
//
// The tracks are the point: the video domain is track selection, and a fake
// that reported none would let every cycling test pass by doing nothing.
internal open class FakeVideoBackend : VideoBackend {
    var subtitleTracks: List<SubtitleTrack> = emptyList()
    var audio: List<AudioTrack> = emptyList()
    var levels: List<QualityLevel> = emptyList()

    val seekedTo: MutableList<Double> = mutableListOf()

    private var position: Double = 0.0
    private var chosenSubtitle: SubtitleTrack? = null
    private var chosenAudio: AudioTrack? = null
    private var chosenQuality: QualityLevel? = null
    private var level: Float = 1.0f
    private var rate: Double = 1.0
    private val listeners: MutableMap<String, MutableList<(Any?) -> Unit>> = mutableMapOf()

    fun fire(event: String, data: Any? = null) {
        listeners[event]?.toList()?.forEach { it(data) }
    }

    // Moves the playhead the way a real engine does — by reporting it, not by
    // being asked. A test that set a field would be asserting against a path
    // nothing takes.
    fun tick(seconds: Double) {
        position = seconds
        fire(CanonicalBackendEvent.TIME_UPDATE)
    }

    override suspend fun load(url: String, opts: LoadOptions) {
        fire(CanonicalBackendEvent.LOAD_START)
        fire(CanonicalBackendEvent.LOADED_METADATA)
        fire(CanonicalBackendEvent.CAN_PLAY)
    }

    override suspend fun play() {
        fire(CanonicalBackendEvent.PLAY)
        fire(CanonicalBackendEvent.PLAYING)
    }

    override fun pause(): Unit = fire(CanonicalBackendEvent.PAUSE)
    override fun stop() = Unit
    override fun currentTime(): Double = position

    override fun currentTime(seconds: Double) {
        seekedTo += seconds
        position = seconds
    }

    override fun duration(): Double = 3_600.0
    override fun volume(): Float = level
    override fun volume(value: Float) { level = value }
    override fun mute() = Unit
    override fun unmute() = Unit
    override fun buffered(): Double = position
    override fun playbackRate(): Double = rate
    override fun playbackRate(rate: Double) { this.rate = rate }
    override fun state(): BackendState = BackendState.READY

    override fun on(event: String, fn: (Any?) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(fn)
    }

    override fun off(event: String, fn: (Any?) -> Unit) {
        listeners[event]?.remove(fn)
    }

    override fun audioTracks(): List<AudioTrack> = audio
    override fun audioTrack(): AudioTrack? = chosenAudio ?: audio.firstOrNull()
    override fun audioTrack(track: AudioTrack) { chosenAudio = track }

    override fun subtitleTracks(): List<SubtitleTrack> = subtitleTracks
    override fun subtitleTrack(): SubtitleTrack? = chosenSubtitle
    override fun subtitleTrack(track: SubtitleTrack?) { chosenSubtitle = track }

    override fun qualityLevels(): List<QualityLevel> = levels
    override fun quality(): QualityLevel? = chosenQuality
    override fun quality(level: QualityLevel?) { chosenQuality = level }
}
