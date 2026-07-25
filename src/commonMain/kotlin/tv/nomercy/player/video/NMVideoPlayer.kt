// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.ports.MediaBackend

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
    backend: MediaBackend? = null,
) : ComposedPlayer(backend) {

    private var fullscreenActive: Boolean = false
    private var pipActive: Boolean = false
    private var theaterActive: Boolean = false
    private var stretching: Stretching = Stretching.Uniform
    private var rect: VideoRect? = null

    init {
        register(this)
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

    // Skips to the end of a named region — an intro, a recap, credits. Seeking
    // rather than jumping the queue, so a refused seek refuses the skip and the
    // viewer stays where they were.
    public open suspend fun playSegment(segment: SegmentBoundary) {
        time(segment.endTime)
    }

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
