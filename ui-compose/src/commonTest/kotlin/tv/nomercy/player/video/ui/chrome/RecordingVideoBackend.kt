// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.ui.RecordingBackend

// The recording engine again, with the half only a video engine has.
//
// The tracks and the ladder are the point of it. A chrome asks the player what
// it can switch between, the player asks the engine, and an engine answering
// empty leaves every menu blank — which is a passing test that measured nothing,
// and is exactly the failure the video parameter on ComposedPlayer exists to
// prevent.
class RecordingVideoBackend : RecordingBackend(), tv.nomercy.player.core.ports.VideoBackend {

    private var audio: AudioTrack? = TRACKS.first()
    private var subtitle: SubtitleTrack? = null
    private var level: QualityLevel? = null

    // Every place the chrome asked the film to go. The base drops the value, so a
    // scrubber case had no way to tell a seek that happened from one that did not
    // — and "the bar exists" is what a test says instead.
    val seeks: MutableList<Double> = mutableListOf()

    override fun currentTime(seconds: Double) {
        seeks += seconds
    }

    override fun audioTracks(): List<AudioTrack> = TRACKS

    override fun audioTrack(): AudioTrack? = audio

    override fun audioTrack(track: AudioTrack) {
        audio = track
    }

    override fun subtitleTracks(): List<SubtitleTrack> = SUBTITLES

    override fun subtitleTrack(): SubtitleTrack? = subtitle

    override fun subtitleTrack(track: SubtitleTrack?) {
        subtitle = track
    }

    override fun qualityLevels(): List<QualityLevel> = LEVELS

    override fun quality(): QualityLevel? = level

    override fun quality(level: QualityLevel?) {
        this.level = level
    }

    companion object {
        val TRACKS: List<AudioTrack> = listOf(
            AudioTrack(id = "en", language = "en", label = "English"),
            AudioTrack(id = "nl", language = "nl", label = "Nederlands"),
        )
        val SUBTITLES: List<SubtitleTrack> = listOf(
            SubtitleTrack(id = "en-sdh", language = "en", label = "English SDH"),
        )
        val LEVELS: List<QualityLevel> = listOf(
            QualityLevel(height = 720, bitrate = 2_000_000, codec = "avc1"),
            QualityLevel(height = 1080, bitrate = 5_000_000, codec = "avc1"),
        )
    }
}

// Something to play. The library's own item type carries an id, a title and a
// url and nothing else, so a fixture is three fields rather than a builder.
data class ChromeTestItem(
    override val id: String = "one",
    override val title: String = "The Quiet Earth",
    override val url: String = "file://one.mkv",
) : PlaylistItem
