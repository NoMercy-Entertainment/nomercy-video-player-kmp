// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack

// What the chrome does, as opposed to what a key press does.
//
// The key handler's seam is relative — seek by ten seconds, step the rate along
// the list — because that is what a button press means. A scrubber and a menu
// are absolute: drag to here, play at exactly this speed, use that track. Two
// different questions, so two seams rather than one that has to serve both.
public interface ChromeCommands :
    ChromeTransportCommands,
    ChromeAudioCommands,
    ChromeTrackCommands,
    ChromePresentationCommands

// What plays, and where in it.
public interface ChromeTransportCommands {

    // Absolute, in seconds. A scrubber knows where it was dropped, and giving it
    // a relative seam would make it compute a delta from a position that has
    // moved since it read it.
    public fun seekTo(seconds: Double)

    // Explicit rather than a toggle. A bar drawn from state that has since
    // changed would toggle to the wrong thing, and the viewer pressed the button
    // they could see.
    public fun setPlaying(playing: Boolean)

    public fun next()

    public fun previous()

    // Opening a menu is the chrome's business rather than the player's, but the
    // bar cannot draw one over itself: it does not know how large the surface is
    // or what else is on it. So it asks.
    public fun openAudioMenu()

    public fun openSubtitleMenu()

}

// How loud it is.
public interface ChromeAudioCommands {

    public fun setVolume(percent: Int)

    public fun setMuted(muted: Boolean)
}

// What is chosen from each list.
public interface ChromeTrackCommands {

    // Named rather than indexed. An index into a list that has since changed
    // selects whatever moved into that slot, and track lists change when a
    // stream switches rendition.
    public fun selectQuality(level: QualityLevel?)

    public fun selectAudioTrack(track: AudioTrack)

    // Null is captions off, which is a choice a menu offers as a row.
    public fun selectSubtitleTrack(track: SubtitleTrack?)

}

// How it is shown, and what the viewer was told.
public interface ChromePresentationCommands {

    public fun setRate(rate: Float)

    public fun setFullscreen(fullscreen: Boolean)

    // Cleared by whoever showed it. A message that lingers is one a viewer reads
    // as describing what is happening now.
    public fun dismissMessage()
}
