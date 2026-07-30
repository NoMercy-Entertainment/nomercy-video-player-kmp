// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.Stretching
import tv.nomercy.player.video.ui.chrome.ChromeCommands

/**
 * A stand-in for [ChromeCommands] that answers the whole interface.
 *
 * Every method is an override, including `playQueueItem`, which the interface
 * defaults to nothing so a host predating the seam keeps compiling. A fake that
 * inherited that default would report a playlist card which plays nothing as a
 * card that works, which is the failure the default is a compromise about.
 */
internal open class RecordingMenuCommands : ChromeCommands {
    val calls: MutableList<String> = mutableListOf()
    var lastPlayedItem: String? = null

    override fun playQueueItem(id: String) {
        calls += "playQueueItem"
        lastPlayedItem = id
    }

    override fun seekTo(seconds: Double) { calls += "seekTo" }

    override fun seekBy(deltaSeconds: Float) { calls += "seekBy" }

    override fun setPlaying(playing: Boolean) { calls += "setPlaying" }

    override fun next() { calls += "next" }

    override fun previous() { calls += "previous" }

    override fun openAudioMenu() { calls += "openAudioMenu" }

    override fun openSubtitleMenu() { calls += "openSubtitleMenu" }

    override fun openQualityMenu() { calls += "openQualityMenu" }

    override fun openSpeedMenu() { calls += "openSpeedMenu" }

    override fun openPlaylistMenu() { calls += "openPlaylistMenu" }

    override fun openSettingsMenu() { calls += "openSettingsMenu" }

    override fun setVolume(percent: Int) { calls += "setVolume" }

    override fun setMuted(muted: Boolean) { calls += "setMuted" }

    override fun selectQuality(level: QualityLevel?) { calls += "selectQuality" }

    override fun selectAudioTrack(track: AudioTrack) { calls += "selectAudioTrack" }

    override fun selectSubtitleTrack(track: SubtitleTrack?) { calls += "selectSubtitleTrack" }

    override fun setRate(rate: Float) { calls += "setRate" }

    override fun setFullscreen(fullscreen: Boolean) { calls += "setFullscreen" }

    override fun setTheater(theater: Boolean) { calls += "setTheater" }

    override fun setPip(pip: Boolean) { calls += "setPip" }

    override fun cycleAspectRatio() { calls += "cycleAspectRatio" }

    override fun setAspectRatio(value: Stretching) { calls += "setAspectRatio" }

    override fun setSubtitleStyle(style: SubtitleStyle) { calls += "setSubtitleStyle" }

    override fun setAutoSkipChapters(enabled: Boolean) { calls += "setAutoSkipChapters" }

    override fun setShowRemaining(show: Boolean) { calls += "setShowRemaining:$show" }

    override fun dismissMessage() { calls += "dismissMessage" }
}
