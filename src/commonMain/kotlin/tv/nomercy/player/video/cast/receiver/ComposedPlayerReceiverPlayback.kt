// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.player.ActionOptions
import tv.nomercy.player.core.player.ActionSource
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.video.cast.RemoteQualityLevel
import tv.nomercy.player.video.item.VideoPlaylistItem
import tv.nomercy.player.video.item.WatchProgress

// P22b Task 3 — the real ReceiverPlayback, wired to the same ComposedPlayer
// every other surface drives, not to the deprecated app engine TvControlServer
// still sits on top of. A television being cast to is a viewer session like
// any other; the whole point of ReceiverSession is that whatever answers a
// command does not need to know it arrived over a socket instead of a tap.
//
// Every write here is tagged ActionSource.REMOTE, mirroring
// PlayerTransportCommands — a chrome watching for a self-caused vs.
// caster-caused change tells them apart the same way regardless of which
// remote surface is driving.
public class ComposedPlayerReceiverPlayback(
    private val player: ComposedPlayer,
    private val scope: CoroutineScope,
) : ReceiverPlayback {

    private val remote = ActionOptions(source = ActionSource.REMOTE)

    override fun apply(command: ReceiverCommand) {
        when (command) {
            is ReceiverCommand.Launch -> scope.launch {
                player.load(ReceiverLaunchItem(command.url), LoadOptions(autoplay = command.autoplay))
            }

            is ReceiverCommand.Play -> scope.launch { player.play(remote) }
            is ReceiverCommand.Pause -> scope.launch { player.pause(remote) }
            is ReceiverCommand.Stop -> scope.launch { player.stop(remote) }
            is ReceiverCommand.Next -> scope.launch { player.next(remote) }
            is ReceiverCommand.Previous -> scope.launch { player.previous(remote) }
            is ReceiverCommand.Seek -> scope.launch { player.time(command.positionMs / MS_PER_SECOND, remote) }

            is ReceiverCommand.SetVolume -> scope.launch { setVolume(command) }
            is ReceiverCommand.SetAudioTrack -> scope.launch { setAudioTrack(command) }
            is ReceiverCommand.SetSubtitleTrack -> scope.launch { setSubtitleTrack(command) }
            is ReceiverCommand.SetQuality -> scope.launch { setQuality(command) }
        }
    }

    // delta wins over level, both zero plus muted toggles mute — the same
    // precedence TvControlServer's /session/volume uses, so a sender written
    // against that route behaves identically against this transport.
    private suspend fun setVolume(command: ReceiverCommand.SetVolume) {
        when {
            command.delta != null && command.delta != 0.0 -> {
                val next = ((player.volume() / VOLUME_SCALE) + command.delta)
                    .coerceIn(0.0, 1.0)
                player.volume((next * VOLUME_SCALE).toInt(), remote)
            }
            command.muted == true -> player.mute(remote)
            command.muted == false -> player.unmute(remote)
            command.level != null -> player.volume(
                (command.level.coerceIn(0.0, 1.0) * VOLUME_SCALE).toInt(),
                remote,
            )
        }
    }

    private suspend fun setAudioTrack(command: ReceiverCommand.SetAudioTrack) {
        val index: Int = command.trackId.toIntOrNull() ?: return
        player.audioTrackMode(index)
    }

    private suspend fun setSubtitleTrack(command: ReceiverCommand.SetSubtitleTrack) {
        val index: Int? = command.trackId?.toIntOrNull()
        player.subtitle(index?.let { player.subtitles().getOrNull(it) })
    }

    // Pick against the filtered (video-only) list, then re-derive the real
    // index against the UNFILTERED list qualityMode() indexes — same idiom as
    // NMVideoPlayer's own quality menu.
    private suspend fun setQuality(command: ReceiverCommand.SetQuality) {
        val levels: List<QualityLevel> = player.qualityLevels().filter { it.height > 0 }
        val pick: QualityLevel? = when (command.level) {
            RemoteQualityLevel.AUTO -> null
            RemoteQualityLevel.LOW -> levels.firstOrNull()
            RemoteQualityLevel.MEDIUM -> levels.getOrNull(levels.size / 2)
            RemoteQualityLevel.HIGH -> levels.lastOrNull()
        }
        player.qualityMode(pick?.let { player.qualityLevels().indexOf(it) })
    }

    private companion object {
        const val MS_PER_SECOND: Double = 1000.0
        const val VOLUME_SCALE: Double = 100.0
    }
}

// A cast Launch names only a URL — no runtime, no artwork, none of the
// metadata a host's own catalogue item would carry. This is that bare
// minimum, not a stand-in for VideoPlaylistItem generally: a real receiver
// wiring should replace this with the app's own item once it can resolve
// the URL back to one (P22b.5's device gate is where that gap would show).
private class ReceiverLaunchItem(override val url: String) : VideoPlaylistItem {
    override val id: String = url
    override val title: String? = null
    override val durationSeconds: Double? = null
    override val progress: WatchProgress? = null
}
