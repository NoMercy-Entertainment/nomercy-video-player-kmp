// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.core.events.SubtitlesPayload
import tv.nomercy.player.core.events.Subscription
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.player.AudioTrackState
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.core.ports.MediaBackend
import tv.nomercy.player.core.ports.VideoBackend

// A video player.
//
// It is a core player plus six things only video has: fullscreen,
// picture-in-picture, theater, how the picture is fitted, where it ended up on
// screen, and skipping a named segment. Everything else — play, queue, volume,
// plugins, state — comes from the core composition and is not restated here.
//
// The view-mode methods only record and announce the state. A player cannot put
// itself fullscreen: that belongs to the Activity, the window or the browser,
// and a library that tried would be fighting whichever one it guessed wrong
// about. A chrome listens for the event and does it, then the state here agrees
// with what the viewer sees.
// Six domain concepts, each with a getter, a setter and mostly a toggle. The
// width is the surface a video chrome needs, and splitting it into a
// ViewModeController the player then forwards to would add a layer whose only
// job is to be passed through.
@Suppress("TooManyFunctions")
public open class NMVideoPlayer(
    private val backend: MediaBackend? = null,
    // The same engine again when it can report tracks and a quality ladder.
    //
    // Passed rather than cast. Core takes these as two parameters precisely so
    // nothing has to ask a MediaBackend whether it is really a VideoBackend, and
    // a video player that skipped this hands core an engine it cannot ask for a
    // subtitle — which is exactly what happened here: the whole track surface
    // answered empty and every menu built from it was blank.
    video: VideoBackend? = null,
) : ComposedPlayer(backend, video = video) {

    // A video backend is both, so a caller with one says so once.
    public constructor(video: VideoBackend) : this(video, video)

    private var fullscreenActive: Boolean = false
    private var pipActive: Boolean = false
    private var theaterActive: Boolean = false
    private var stretching: Stretching = Stretching.Uniform
    private var rect: VideoRect? = null

    // The video half of what the engine reports. Core's bridge handles the
    // transport events; these are the ones only a video player has a name for.
    public val videoBridge: VideoBackendBridge = VideoBackendBridge(context)

    init {
        register(this)
        backend?.let { videoBridge.attach(it) }
    }

    public open fun fullscreen(): Boolean = fullscreenActive

    public open fun fullscreen(active: Boolean) {
        if (fullscreenActive == active) return
        fullscreenActive = active
        // Leaving fullscreen and entering theater are different intents, and a
        // chrome cannot be in both. Fullscreen wins because it is the more
        // specific request.
        if (active) theaterActive = false
        emit(VideoEvents.Fullscreen, ViewModeChange(active))
    }

    public open fun toggleFullscreen(): Unit = fullscreen(!fullscreenActive)

    public open fun pip(): Boolean = pipActive

    public open fun pip(active: Boolean) {
        if (pipActive == active) return
        pipActive = active
        emit(VideoEvents.Pip, ViewModeChange(active))
    }

    public open fun togglePip(): Unit = pip(!pipActive)

    public open fun theater(): Boolean = theaterActive

    public open fun theater(active: Boolean) {
        if (theaterActive == active) return
        theaterActive = active
        if (active) fullscreenActive = false
        emit(VideoEvents.Theater, ViewModeChange(active))
    }

    public open fun toggleTheater(): Unit = theater(!theaterActive)

    public open fun aspectRatio(): Stretching = stretching

    public open fun aspectRatio(value: Stretching) {
        if (stretching == value) return
        stretching = value
        emit(VideoEvents.AspectRatio, AspectRatioChange(value))
    }

    // Steps through the modes in the order a viewer pressing one button expects,
    // which is why it is a method rather than something every chrome reimplements.
    public open fun cycleAspectRatio() {
        val modes: List<Stretching> = Stretching.entries
        aspectRatio(modes[(modes.indexOf(stretching) + 1) % modes.size])
    }

    public open fun videoRect(): VideoRect? = rect

    // Reported by whatever is drawing, because only it knows. Null until
    // something has been rendered, which an overlay positioning itself has to
    // handle rather than assume zero.
    public open fun videoRect(value: VideoRect?) {
        if (rect == value) return
        rect = value
        emit(VideoEvents.VideoRect, VideoRectChange(value))
    }

    // Plays a named region and says when the playhead leaves it.
    //
    // The window is the point. A "skip intro" button needs to appear while the
    // intro is playing and disappear when it ends, and only something watching
    // the playhead knows when that is. Seeking to the start rather than jumping
    // the queue means a refused seek refuses the whole thing and the viewer
    // stays where they were.
    //
    // Replaces any window already open. Two watchers on one playhead both fire,
    // and the second one's boundary is about a region the viewer has left.
    public open suspend fun playSegment(segment: SegmentBoundary) {
        clearSegment()
        time(segment.startTime)

        segmentWatch = on(CoreEvents.Time) { update ->
            if (update.time >= segment.endTime) {
                // Cleared before announcing, so a listener that starts another
                // segment from inside the callback is not immediately undone by
                // this one finishing.
                clearSegment()
                emit(VideoEvents.SegmentBoundary, segment)
            }
        }
    }

    // Stops watching for the end of the current region.
    //
    // A chrome dismissing its own "skip intro" button calls this, and so does
    // anything that changed the item underneath a window that is still open —
    // a watcher left running across a track change fires on a timestamp that
    // means something else now.
    public open fun clearSegment() {
        segmentWatch?.dispose()
        segmentWatch = null
    }

    private var segmentWatch: Subscription? = null

    // ── Subtitles ────────────────────────────────────────────────────────────

    // Whether the subtitle showing was chosen or fell out of the engine's
    // defaults, exactly as audioTrackMode answers for audio.
    //
    // A chrome needs it to decide whether to tick a language in a menu, and a
    // per-library player needs it to know whether to carry a choice into the
    // next episode or let the engine decide again.
    public open fun subtitleState(): AudioTrackState = subtitleChoice

    // How subtitles should be drawn.
    //
    // Held here and announced rather than applied, because core has no renderer:
    // libass draws them on three platforms and a Compose overlay on none of
    // them. The renderer listens and restyles; this is the one place the
    // viewer's preference lives so every renderer reads the same answer.
    public open fun subtitleStyle(): SubtitleStyle = cueStyle

    public open fun subtitleStyle(style: SubtitleStyle) {
        cueStyle = style
        emit(CoreEvents.SubtitleStyle, style)
    }

    // The next subtitle track, wrapping through off.
    //
    // Off is part of the cycle rather than a separate control, because that is
    // what a remote's subtitle button does: press it enough times and the
    // subtitles go away. A cycle that never reached off would trap a viewer who
    // turned them on by accident.
    public open fun cycleSubtitles() {
        val available: List<SubtitleTrack?> = subtitles() + listOf(null)
        if (available.size <= 1) return

        val here: Int = available.indexOfFirst { it?.id == subtitle()?.id }
        subtitle(available[(here + 1) % available.size])
        subtitleChoice = AudioTrackState.MANUAL
    }

    // The next audio track, wrapping. No off: an item with no audio is not a
    // state a viewer can ask for, and a cycle that produced silence would look
    // like a broken track rather than a choice.
    public open fun cycleAudioTracks() {
        val available: List<AudioTrack> = audioTracks()
        if (available.size <= 1) return

        val here: Int = available.indexOfFirst { it.id == audioTrack()?.id }
        audioTrack(available[(here + 1) % available.size])
    }

    // A subtitle file the item did not come with.
    //
    // The everyday case is a sidecar the viewer downloaded, or one the server
    // found after the item was already playing. Added to what the engine
    // reported rather than replacing it, and kept here rather than pushed into
    // the engine, because not every engine accepts a track after loading and
    // the ones that refuse would silently drop it.
    public open fun addSubtitleTrack(track: SubtitleTrack) {
        externalSubtitles = externalSubtitles.filterNot { it.id == track.id } + track
        emit(CoreEvents.Subtitles, SubtitlesPayload(subtitles()))
    }

    public open fun removeSubtitleTrack(id: String) {
        val without: List<SubtitleTrack> = externalSubtitles.filterNot { it.id == id }
        if (without.size == externalSubtitles.size) return

        externalSubtitles = without
        // Showing a track that has just been removed is the failure this
        // prevents: the renderer keeps drawing cues from a file nobody can
        // select any more.
        if (subtitle()?.id == id) subtitle(null)
        emit(CoreEvents.Subtitles, SubtitlesPayload(subtitles()))
    }

    // What the engine reported, plus what the host added.
    override fun subtitles(): List<SubtitleTrack> = super.subtitles() + externalSubtitles

    // The seam every item passes through on the way into the queue.
    //
    // Video items arrive from a server that sends what it has, and what it has
    // differs by scan age: a title in one field or another, a duration in
    // seconds or milliseconds. Normalising here rather than at each call site
    // means a consumer building a queue by hand gets the same treatment as one
    // loading a playlist.
    //
    // Identity by default. Core cannot know a wire format it was never told
    // about, and a normaliser that guessed would corrupt the fields it guessed
    // wrong.
    public open fun normalizePlaylistItem(item: PlaylistItem): PlaylistItem = item

    private var subtitleChoice: AudioTrackState = AudioTrackState.DEFAULT
    private var cueStyle: SubtitleStyle = SubtitleStyle()
    private var externalSubtitles: List<SubtitleTrack> = emptyList()

    public open fun message(text: String, ms: Double? = null) {
        emit(VideoEvents.DisplayMessage, DisplayMessage(text, ms))
    }

    public open fun clearMessage() {
        emit(VideoEvents.RemoveMessage, Unit)
    }

    public companion object {
        private val live: MutableList<NMVideoPlayer> = mutableListOf()

        // Every player built, so a host with more than one on screen can find
        // them without threading a reference through its whole UI. The web trio
        // keeps the same registry for the same reason.
        public fun instances(): List<NMVideoPlayer> = live.toList()

        private fun register(player: NMVideoPlayer) {
            live.add(player)
        }
    }
}
