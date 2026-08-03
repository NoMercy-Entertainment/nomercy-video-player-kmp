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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tv.nomercy.player.core.events.Subscription
import tv.nomercy.player.video.VideoEvents
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.BufferState
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerState
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.item.VideoPlaylistItem
import tv.nomercy.player.video.item.WatchProgress
import tv.nomercy.player.video.item.normalizeWatchProgress
import tv.nomercy.player.video.tv.TvChapter
import tv.nomercy.player.video.tv.TvChromeItem
import kotlin.math.roundToInt
import tv.nomercy.player.core.ports.QualityMode

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
    error: ChromeError? = null,
    itemOf: (PlaylistItem?) -> TvChromeItem? = ::chromeItemOf,
): ChromeState {
    val snapshot: PlayerState by player.stateFlow.collectAsState()

    // Fullscreen, theater, picture-in-picture and the aspect ratio are read off
    // the player below and live in none of them: [PlayerState] has no field for
    // any of them, so nothing about changing one produces a new snapshot and
    // this composable had no reason to run again.
    //
    // The projection was therefore right exactly once, at the composition that
    // built it. A fullscreen button drawn from it never changed its icon, and a
    // gesture that toggles ran every time against `fullscreen = false`.
    // Read here so the composition depends on it. That read IS the subscription:
    // the value itself says nothing, and dropping it would take the recomposition
    // with it.
    val viewMode: Int = rememberViewModeRevision(player)

    return remember(player, snapshot, viewMode, message, error) {
        chromeStateOf(
            player,
            snapshot,
            itemOf(snapshot.item),
            message,
            error,
            // Through the SAME hook the playing item goes through, so the queue and
            // the title bar cannot disagree about what an item is called.
            //
            // This was not passed at all, so `ChromeState.queue` was empty on every
            // real player and the playlist pane drew nothing — a pane with a button
            // that opened it, a row in the settings list that opened it, and no
            // content either way.
            player.queue().mapNotNull(itemOf),
        )
    }
}

// A counter that moves whenever the player changes something the projection
// reads and [PlayerState] does not carry.
//
// A count rather than the values themselves, because what it is for is being a
// recomposition key: the projection below already knows how to read each one off
// the player, and mirroring four of them here would be a second copy to keep in
// step with the first.
@Composable
private fun rememberViewModeRevision(player: NMVideoPlayer): Int {
    var revision: Int by remember(player) { mutableStateOf(0) }

    // Named one at a time rather than looped over a list of keys: the key's type
    // parameter is what makes the payload safe, and a `List<EventKey<*>>` gives
    // that away for four lines of brevity.
    DisposableEffect(player) {
        val subscriptions: List<Subscription> = listOf(
            player.on(VideoEvents.Fullscreen) { revision += 1 },
            player.on(VideoEvents.Theater) { revision += 1 },
            player.on(VideoEvents.Pip) { revision += 1 },
            player.on(VideoEvents.AspectRatio) { revision += 1 },
        )

        onDispose { subscriptions.forEach { it.dispose() } }
    }

    return revision
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
    error: ChromeError? = null,
    queue: List<TvChromeItem> = emptyList(),
): ChromeState = ChromeState(
    playing = snapshot.playState == PlayState.PLAYING,
    // Anything other than idle means the picture is not advancing, which is the
    // one question a spinner answers. Naming the three busy states separately
    // would be three chances to add a fourth and forget this.
    buffering = snapshot.bufferState != BufferState.IDLE,
    timeSeconds = snapshot.time,
    durationSeconds = snapshot.duration,
    // The player's own frontier, which is an absolute position on the same
    // timeline as the duration below it.
    //
    // This walked the engine's ranges here instead, because one backend answered
    // `buffered` with the furthest end out of every range and a bar drawn from it
    // promised an hour of buffer over a hole. That is fixed where it was wrong —
    // MediaBackend derives the frontier from the ranges for every engine that
    // reports them — so a chrome working around it now would be a second walk to
    // keep in step with the first.
    bufferedFraction = fractionOf(player.buffered(), snapshot.duration),
    volume = snapshot.volume,
    muted = snapshot.muted,
    chapters = player.chapters().map { TvChapter(it.startTime, it.title) },
    qualityLevels = player.qualityLevels(),
    activeQuality = player.quality(),
    qualityAuto = player.qualityMode() == QualityMode.AUTO,
    audioTracks = player.audioTracks(),
    activeAudio = player.audioTrack(),
    subtitleTracks = player.subtitles(),
    activeSubtitle = player.subtitle(),
    rate = snapshot.playbackRate.toFloat(),
    item = item,
    queue = queue,
    queueSize = snapshot.queueLength,
    queueIndex = snapshot.index,
    fullscreen = player.fullscreen(),
    aspectRatio = player.aspectRatio(),
    subtitleStyle = player.subtitleStyle(),
    message = message,
    error = error,
)

// What the library can honestly say about what is playing.
//
// A show name, a season and an episode number are the host's, arriving from
// whichever server it talks to, so the itemOf hook is how it supplies those
// rather than this inventing a wire format and quietly mis-parsing everybody
// else's.
//
// The runtime and the continue-watching bar are different: an item that
// implements [VideoPlaylistItem] states both, and this read neither — so a host
// passing a server payload and using the default got a playlist of bare titles
// while the fields to draw them sat on the item it had handed over.
//
// The id is not decoration. It is what a chosen playlist card plays, so a host
// overriding this and dropping it gets a pane whose rows do nothing.
public fun chromeItemOf(item: PlaylistItem?): TvChromeItem? {
    if (item == null) return null

    val video: VideoPlaylistItem? = item as? VideoPlaylistItem
    // Through the normaliser, not straight off the item. An older server sends a
    // date and a position and no percentage at all, and reading `percentage`
    // directly there draws every part-watched episode as untouched.
    val progress: WatchProgress? = normalizeWatchProgress(video?.progress, video?.durationSeconds)

    return TvChromeItem(
        title = item.title,
        id = item.id,
        durationSeconds = video?.durationSeconds,
        progressPercent = progress?.percentage?.roundToInt(),
        // `readItemImage`: image, then poster, then thumbnail, first one present.
        //
        // All three fields are on [VideoPlaylistItem] with that precedence
        // written down beside them, and this read none of them — so every
        // playlist card drew an empty grey rectangle where the episode still
        // belongs, on a player whose items were carrying the URL the whole time.
        image = video?.image ?: video?.poster ?: video?.thumbnail,
    )
}

// Zero rather than a division by zero, which is every live stream: the duration
// is unknown and a bar has nothing to fill against.
private fun fractionOf(part: Double, whole: Double): Float =
    if (whole <= 0.0) 0f else (part / whole).coerceIn(0.0, 1.0).toFloat()
