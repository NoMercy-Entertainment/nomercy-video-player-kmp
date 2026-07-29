// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.video.Stretching
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.BufferState
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerState
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.tv.TvChapter
import tv.nomercy.player.video.tv.TvChromeItem

// The player, as the chrome reads it.
//
// One direction only. Everything the widgets draw comes through here and
// everything they do goes back through ChromeCommands, which is what keeps a
// widget from reaching into the player for the one field its projection forgot
// and then going stale in a way nobody notices.
@Composable
public fun rememberChromeState(
    player: NMVideoPlayer,
    message: String? = null,
    itemOf: (PlaylistItem?) -> TvChromeItem? = ::chromeItemOf,
): ChromeState {
    val snapshot: PlayerState by player.stateFlow.collectAsState()

    return chromeStateOf(player, snapshot, itemOf(snapshot.item), message)
}

// Split from the composable so it can be driven from a fixture.
//
// The lists are read off the player rather than off the snapshot because the
// snapshot has no room for them: a track list is not something that changes per
// frame, and putting it in the per-frame value would copy four lists a second
// for a menu nobody has opened.
public fun chromeStateOf(
    player: NMVideoPlayer,
    snapshot: PlayerState,
    item: TvChromeItem?,
    message: String? = null,
): ChromeState = ChromeState(
    playing = snapshot.playState == PlayState.PLAYING,
    // Anything other than idle means the picture is not advancing, which is the
    // one question a spinner answers. Naming the three busy states separately
    // would be three chances to add a fourth and forget this.
    buffering = snapshot.bufferState != BufferState.IDLE,
    timeSeconds = snapshot.time,
    durationSeconds = snapshot.duration,
    bufferedFraction = fractionOf(snapshot.buffered, snapshot.duration),
    volume = snapshot.volume,
    muted = snapshot.muted,
    chapters = player.chapters().map { TvChapter(it.startTime, it.title) },
    qualityLevels = player.qualityLevels(),
    activeQuality = player.quality(),
    audioTracks = player.audioTracks(),
    activeAudio = player.audioTrack(),
    subtitleTracks = player.subtitles(),
    activeSubtitle = player.subtitle(),
    rate = snapshot.playbackRate.toFloat(),
    item = item,
    queueSize = snapshot.queueLength,
    queueIndex = snapshot.index,
    fullscreen = player.fullscreen(),
    aspectRatio = player.aspectRatio(),
    subtitleStyle = player.subtitleStyle(),
    message = message,
)

// What the library can honestly say about what is playing.
//
// A playlist item carries an id, a title and a url, and that is the whole of it:
// a show name, a season and an episode number are the host's, arriving from
// whichever server it talks to. So the default fills the one field it has and
// the itemOf hook is how a host supplies the rest, rather than this inventing a
// wire format and quietly mis-parsing everybody else's.
public fun chromeItemOf(item: PlaylistItem?): TvChromeItem? =
    item?.let { TvChromeItem(title = it.title) }

// Zero rather than a division by zero, which is every live stream: the duration
// is unknown and a bar has nothing to fill against.
private fun fractionOf(part: Double, whole: Double): Float =
    if (whole <= 0.0) 0f else (part / whole).coerceIn(0.0, 1.0).toFloat()
