// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.video.ui.chrome.menus.SubtitleStyle
import tv.nomercy.player.video.Stretching
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack

/**
 * One stand-in for [ChromeCommands], for every device test that needs one.
 *
 * There were three, each hand-rolled next to the test that used it, and every
 * one of them stopped compiling the day the interface grew a menu — three
 * identical breakages from one change, in a source set no local gate builds, so
 * they sat broken. Adding the seven missing overrides three times would buy the
 * same failure the next time a command is added.
 *
 * So it records everything and each test reads the field it cares about. A fake
 * that answers the whole interface is the one that survives the interface
 * changing.
 */
internal open class RecordingChromeCommands : ChromeCommands {
    val calls: MutableList<String> = mutableListOf()
    val seeks: MutableList<Float> = mutableListOf()

    var playing: Boolean? = null
    var fullscreen: Boolean? = null
    var volume: Int? = null
    var lastQuality: QualityLevel? = null
    var qualityWasSet: Boolean = false
    var lastAudio: AudioTrack? = null
    var lastSubtitle: SubtitleTrack? = null
    var subtitleWasSet: Boolean = false
    var lastRate: Float? = null
    var lastPlayedItem: String? = null

    override fun seekTo(seconds: Double) { calls += "seekTo" }

    override fun seekBy(deltaSeconds: Float) {
        calls += "seekBy"
        seeks += deltaSeconds
    }

    override fun setPlaying(playing: Boolean) {
        calls += "setPlaying"
        this.playing = playing
    }

    override fun next() { calls += "next" }

    override fun previous() { calls += "previous" }

    override fun openAudioMenu() { calls += "openAudioMenu" }

    override fun openSubtitleMenu() { calls += "openSubtitleMenu" }

    override fun openQualityMenu() { calls += "openQualityMenu" }

    override fun openSpeedMenu() { calls += "openSpeedMenu" }

    override fun openPlaylistMenu() { calls += "openPlaylistMenu" }

    override fun openSettingsMenu() { calls += "openSettingsMenu" }

    // Overridden even though the interface defaults it, because the default is
    // the do-nothing a host gets for free and a fake that inherited it would
    // report a card that plays nothing as a card that works.
    override fun playQueueItem(id: String) {
        calls += "playQueueItem"
        lastPlayedItem = id
    }

    override fun setVolume(percent: Int) {
        calls += "setVolume"
        volume = percent
    }

    override fun setMuted(muted: Boolean) { calls += "setMuted" }

    override fun selectQuality(level: QualityLevel?) {
        calls += "selectQuality"
        lastQuality = level
        qualityWasSet = true
    }

    override fun selectAudioTrack(track: AudioTrack) {
        calls += "selectAudioTrack"
        lastAudio = track
    }

    override fun selectSubtitleTrack(track: SubtitleTrack?) {
        calls += "selectSubtitleTrack"
        lastSubtitle = track
        subtitleWasSet = true
    }

    override fun setRate(rate: Float) {
        calls += "setRate"
        lastRate = rate
    }

    override fun setFullscreen(fullscreen: Boolean) {
        calls += "setFullscreen"
        this.fullscreen = fullscreen
    }

    override fun setTheater(theater: Boolean) { calls += "setTheater" }

    override fun setPip(pip: Boolean) { calls += "setPip" }

    override fun cycleAspectRatio() { calls += "cycleAspectRatio" }

    override fun setAspectRatio(value: Stretching) { calls += "setAspectRatio:${value.token}" }

    // Both were added to ChromeCommands and never to this double, so the whole
    // androidDeviceTest source set stopped compiling — and native-gate.sh does not
    // build that source set, so it reported green while the entire on-device render
    // gate could not run at all. A gate that cannot compile cannot go red.
    override fun setAutoSkipChapters(enabled: Boolean) { calls += "setAutoSkipChapters:$enabled" }

    override fun setShowRemaining(show: Boolean) { calls += "setShowRemaining:$show" }

    override fun setSubtitleStyle(style: SubtitleStyle) { calls += "setSubtitleStyle" }

    override fun dismissMessage() { calls += "dismissMessage" }
}
