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

            is ReceiverCommand.SetVolume -> scope.launch {
                // delta wins over level, both zero plus muted toggles mute —
                // the same precedence TvControlServer's /session/volume uses,
                // so a sender written against that route behaves identically
                // against this transport.
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

            is ReceiverCommand.SetAudioTrack -> scope.launch {
                val index = command.trackId.toIntOrNull() ?: return@launch
                player.audioTrackMode(index)
            }

            is ReceiverCommand.SetSubtitleTrack -> scope.launch {
                val index = command.trackId?.toIntOrNull()
                player.subtitle(index?.let { player.subtitles().getOrNull(it) })
            }

            is ReceiverCommand.SetQuality -> scope.launch {
                val levels = player.qualityLevels().filter { it.height > 0 }
                val index = when (command.level) {
                    tv.nomercy.player.video.cast.RemoteQualityLevel.AUTO -> null
                    tv.nomercy.player.video.cast.RemoteQualityLevel.LOW -> levels.indices.firstOrNull()
                    tv.nomercy.player.video.cast.RemoteQualityLevel.MEDIUM -> levels.indices.firstOrNull()
                        ?.let { levels.size / 2 }
                    tv.nomercy.player.video.cast.RemoteQualityLevel.HIGH -> levels.indices.lastOrNull()
                }
                player.qualityMode(index)
            }
        }
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
