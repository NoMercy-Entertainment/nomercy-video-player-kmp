// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import tv.nomercy.player.core.media.Chapter

// Everything a key press can ask the player to do.
//
// A contract rather than the player itself, for two reasons. A key handler that
// held a player could reach anything on it, and the set of things a keyboard is
// allowed to do is deliberately smaller. And a binding table tested against a
// real player is testing the player.
//
// Split five ways because one interface of two dozen methods is a thing nobody
// implements without a checklist, and because they are genuinely separate
// concerns: what is playing, what it currently is, how it looks, how loud it is,
// and how much of the screen it has. A host that only has volume to offer
// implements one of them.
public class PlayerCommands(
    public val transport: VideoTransport,
    public val presentation: VideoPresentation,
    public val volume: VideoVolume,
    public val state: VideoState,
    /**
     * The fullscreen seam, which is the one the player genuinely does not own.
     *
     * An Activity, an NSWindow or a browser decides whether the picture fills
     * the screen; the player only records what it was told. It sat on
     * [VideoPresentation] beside the track cycling for a while, which read as
     * one more thing about how the picture looks and is not — and it is the
     * only seam here a host can honestly have no answer for.
     */
    public val window: VideoWindow,
)

// What is playing and where in it.
public interface VideoTransport {

    public fun togglePlay()

    public fun play()

    public fun pause()

    public fun stop()

    // Signed, in seconds. One method rather than a forward and a back, because
    // every caller of those two passes the same magnitude with a different sign
    // and the pair invites one of them being wired the wrong way round.
    public fun seekBy(deltaSeconds: Float)

    public fun next()

    public fun previous()

    public fun nextChapter()

    public fun previousChapter()
}

// What the player currently is, as opposed to what it can be asked to do.
//
// Separate because only two bindings read anything at all — the frame advance
// needs to know it is paused, and the time key needs the numbers. Mixing them in
// with the commands means every host implementing a transport also answers
// questions it has no reason to be asked.
public interface VideoState {

    public fun time(): Double

    public fun duration(): Double

    public fun isPlaying(): Boolean

    // What the info panel needs beyond the numbers: which chapter the viewer is
    // in. Defaulted to empty rather than added as a requirement, so a host that
    // implemented this before chapters existed still compiles — and empty means
    // "this host cannot say", which is the same convention the backend's own
    // range methods use.
    public fun chapters(): List<Chapter> = emptyList()

    // What is playing, by name. Null rather than a placeholder: the word a
    // viewer reads when nothing is named is translated, so choosing it here
    // would put English on a television in seventy-eight locales.
    public fun title(): String? = null
}

// How it is shown, which includes the two things a key press changes about the
// picture and the two tracks a viewer switches between.
public interface VideoPresentation {

    public fun cycleSubtitles()

    public fun cycleAudioTracks()

    public fun cycleAspectRatio()

    public fun rate(): Float

    public fun rate(value: Float)

    public fun rates(): List<Float>

    // For the presses that are not the player's business at all. Subtitle size
    // belongs to whatever is drawing the subtitles, and a key handler that
    // reached into it would be one that knows how they are rendered.
    public fun emit(name: String)

    // What the viewer is told. A speed change with no feedback is a film that
    // quietly sounds wrong, and on a television there is no status bar to check.
    public fun message(text: String)
}

// How much of the screen the picture has.
//
// Two members and neither belongs to the player. The window decides, the player
// records, and the key handler needs both halves for one press: Escape may only
// LEAVE fullscreen, so it has to ask before it acts.
public interface VideoWindow {

    public fun toggleFullscreen()

    /**
     * Whether the picture is filling the screen right now.
     *
     * Only Escape asks, and the answer decides whether the press is CONSUMED
     * rather than what happens: a host that is not fullscreen wants Escape back,
     * to close the player with. Answering it wrong does not misbehave, it steals
     * a key — which is why the question exists rather than a bare exit call that
     * fires either way.
     */
    public fun isFullscreen(): Boolean
}

public interface VideoVolume {

    public fun volumeUp()

    public fun volumeDown()

    public fun toggleMute()
}
