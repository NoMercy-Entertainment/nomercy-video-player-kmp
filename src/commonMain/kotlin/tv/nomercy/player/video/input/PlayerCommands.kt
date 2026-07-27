// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

// Everything a key press can ask the player to do.
//
// A contract rather than the player itself, for two reasons. A key handler that
// held a player could reach anything on it, and the set of things a keyboard is
// allowed to do is deliberately smaller. And a binding table tested against a
// real player is testing the player.
//
// Split four ways because one interface of two dozen methods is a thing nobody
// implements without a checklist, and because they are genuinely separate
// concerns: what is playing, what it currently is, how it looks, and how loud it
// is. A host that only has volume to offer implements one of them.
public class PlayerCommands(
    public val transport: VideoTransport,
    public val presentation: VideoPresentation,
    public val volume: VideoVolume,
    public val state: VideoState,
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
}

// How it is shown, which includes the two things a key press changes about the
// picture and the two tracks a viewer switches between.
public interface VideoPresentation {

    public fun cycleSubtitles()

    public fun cycleAudioTracks()

    public fun toggleFullscreen()

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

public interface VideoVolume {

    public fun volumeUp()

    public fun volumeDown()

    public fun toggleMute()
}
