// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.nomercy.player.core.media.Chapter
import tv.nomercy.player.core.player.PlayState
import tv.nomercy.player.core.player.PlayerState
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.chrome.chromeItemOf
import tv.nomercy.player.video.ui.chrome.rememberChromeScheduler
import tv.nomercy.player.video.tv.TvChapter
import tv.nomercy.player.video.tv.TvChromeCallbacks
import tv.nomercy.player.video.tv.TvChromeContent
import tv.nomercy.player.video.tv.TvChromeController
import tv.nomercy.player.video.tv.TvContentCallbacks
import tv.nomercy.player.video.tv.TvEpisode
import tv.nomercy.player.video.tv.TvTrack
import tv.nomercy.player.video.tv.TvTransportState

// The television chrome, bound to a player.
//
// The state machine and its widgets were built to take a controller and a
// snapshot rather than a player, which is what let them be tested against
// fixtures and what keeps a set-top box build free of anything a phone needs.
// The cost of that is that somebody has to do this, and until now the somebody
// was every consumer.
public class TvChrome(
    public val controller: TvChromeController,
    public val transport: TvTransportState,
    public val content: TvChromeContent,
)

// The lists and the episodes come from different places on purpose. Tracks are
// the engine's and are read off the player; episodes are the host's library and
// a player has no business asking what is in it.
@Composable
public fun rememberTvChrome(
    player: NMVideoPlayer,
    episodes: List<TvEpisode> = emptyList(),
    onExit: () -> Unit = {},
    onSelectEpisode: (String) -> Unit = {},
    onSearchSubtitles: () -> Unit = {},
): TvChrome {
    val scope: CoroutineScope = rememberCoroutineScope()
    val snapshot: PlayerState by player.stateFlow.collectAsState()

    // What the scrub bar shows while a viewer is still moving it. Held here
    // rather than pushed at the player, because the film has not moved yet:
    // seeking on every step of a scrub is the seek storm the engine answers by
    // stalling.
    var preview: Float? by remember { mutableStateOf(null) }

    val callbacks: TvChromeCallbacks = remember(player, scope) {
        PlayerTvCallbacks(player, scope, onExit) { preview = it }
    }
    val content: TvContentCallbacks = remember(player) {
        PlayerTvContent(player, onSelectEpisode, onSearchSubtitles)
    }
    val scheduler = rememberChromeScheduler()

    val controller: TvChromeController = remember(player, scheduler) {
        TvChromeController(callbacks, scheduler, content = content)
    }

    return TvChrome(
        controller = controller,
        transport = TvTransportState(
            isPlaying = snapshot.playState == PlayState.PLAYING,
            timeSeconds = preview?.toDouble() ?: snapshot.time,
            durationSeconds = snapshot.duration,
            chapters = player.chapters().map(::tvChapterOf),
        ),
        content = TvChromeContent(
            item = chromeItemOf(snapshot.item),
            episodes = episodes,
            audioTracks = player.audioTracks().map { tvTrackOf(it, player.audioTrack()) },
            subtitleTracks = player.subtitles().map { tvTrackOf(it, player.subtitle()) },
        ),
    )
}

internal fun tvChapterOf(chapter: Chapter): TvChapter = TvChapter(chapter.startTime, chapter.title)

internal fun tvTrackOf(track: AudioTrack, current: AudioTrack?): TvTrack =
    TvTrack(track.id, track.label, track.language, track.id == current?.id)

internal fun tvTrackOf(track: SubtitleTrack, current: SubtitleTrack?): TvTrack =
    TvTrack(track.id, track.label, track.language, track.id == current?.id)

private class PlayerTvCallbacks(
    private val player: NMVideoPlayer,
    private val scope: CoroutineScope,
    private val onExit: () -> Unit,
    private val onPreview: (Float?) -> Unit,
) : TvChromeCallbacks {

    override fun play() {
        scope.launch { player.play() }
    }

    override fun pause() {
        scope.launch { player.pause() }
    }

    override fun togglePlay() {
        scope.launch { player.togglePlayback() }
    }

    // Absolute, because this is where a scrub is committed: the bar knows
    // exactly where it was left, and a delta computed from a position that has
    // moved since would land somewhere else.
    override fun seek(seconds: Float) {
        onPreview(null)
        scope.launch { player.time(seconds.toDouble()) }
    }

    override fun overrideTime(seconds: Float?): Unit = onPreview(seconds)

    override fun restart() {
        scope.launch { player.time(0.0) }
    }

    override fun next() {
        scope.launch { player.next() }
    }

    override fun exitPlayer(): Unit = onExit()
}

private class PlayerTvContent(
    private val player: NMVideoPlayer,
    private val onSelectEpisode: (String) -> Unit,
    private val onSearchSubtitles: () -> Unit,
) : TvContentCallbacks {

    override fun selectEpisode(id: String): Unit = onSelectEpisode(id)

    // By identifier against the list the player reported, so a track that has
    // moved since the menu was drawn still selects the one whose name was read.
    override fun selectAudioTrack(id: String) {
        player.audioTracks().firstOrNull { it.id == id }?.let { player.audioTrack(it) }
    }

    // Null is off, which is a row in the list rather than an absence, so an
    // identifier that matches nothing turns them off rather than doing nothing.
    override fun selectSubtitleTrack(id: String) {
        player.subtitle(player.subtitles().firstOrNull { it.id == id })
    }

    override fun searchSubtitlesOnline(): Unit = onSearchSubtitles()
}
