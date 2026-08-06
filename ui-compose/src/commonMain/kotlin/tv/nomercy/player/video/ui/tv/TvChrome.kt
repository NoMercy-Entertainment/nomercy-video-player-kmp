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
import tv.nomercy.player.video.tv.AUTO_HIDE_MS
import tv.nomercy.player.video.tv.TvChapter
import tv.nomercy.player.video.tv.TvChromeCallbacks
import tv.nomercy.player.video.tv.TvChromeContent
import tv.nomercy.player.video.tv.TvChromeController
import tv.nomercy.player.video.tv.TvContentCallbacks
import tv.nomercy.player.video.tv.TvEpisode
import tv.nomercy.player.video.tv.TvTrack
import tv.nomercy.player.video.tv.TvTransportState
import tv.nomercy.player.video.ui.thumbnails.PreviewSprite

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
    /**
     * The decoded sheet the seek strip is drawn from, when the host has one.
     *
     * Carried here rather than on TvTransportState because that type lives in the
     * library's own commonMain, which depends on no UI toolkit at all — a decoded
     * ImageBitmap on it would tie every consumer of a transport snapshot to
     * Compose. This class is already the Compose-side assembly, so it is where a
     * Compose bitmap belongs.
     */
    public val sprite: PreviewSprite? = null,
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
    /**
     * The sheet, from whoever loaded it.
     *
     * A parameter rather than something read off the player, for the reason the
     * episode list is: decoding a sprite sheet is a fetch and a bitmap, and a
     * player library that did it would be choosing a host's image loader for it.
     * loadPreviewSprite builds one; this is the door it goes through on a
     * television, and until it existed there was none.
     */
    previewSprite: PreviewSprite? = null,
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
    val content: TvContentCallbacks = remember(player, scope) {
        PlayerTvContent(player, scope, onSelectEpisode, onSearchSubtitles)
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
        sprite = previewSprite,
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
    private val scope: CoroutineScope,
    private val onSelectEpisode: (String) -> Unit,
    private val onSearchSubtitles: () -> Unit,
) : TvContentCallbacks {

    override fun selectEpisode(id: String): Unit = onSelectEpisode(id)

    // By identifier against the list the player reported, so a track that has
    // moved since the menu was drawn still selects the one whose name was read.
    override fun selectAudioTrack(id: String) {
        player.audioTracks().firstOrNull { it.id == id }?.let { track ->
            // Launched, not awaited: the setter suspends because the reference
            // puts a cancellable before-hook in front of it, and a D-pad menu
            // row is not a suspending call site.
            scope.launch { player.audioTrack(track) }
        }
    }

    // Null is off, which is a row in the list rather than an absence, so an
    // identifier that matches nothing turns them off rather than doing nothing.
    override fun selectSubtitleTrack(id: String) {
        scope.launch { player.subtitle(player.subtitles().firstOrNull { it.id == id }) }
    }

    override fun searchSubtitlesOnline(): Unit = onSearchSubtitles()
}
