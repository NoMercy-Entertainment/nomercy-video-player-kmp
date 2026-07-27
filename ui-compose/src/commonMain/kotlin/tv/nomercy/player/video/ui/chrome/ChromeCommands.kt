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
public interface ChromeCommands {

    // Absolute, in seconds. A scrubber knows where it was dropped, and giving it
    // a relative seam would make it compute a delta from a position that has
    // moved since it read it.
    public fun seekTo(seconds: Double)

    public fun setVolume(percent: Int)

    public fun setMuted(muted: Boolean)

    // Named rather than indexed. An index into a list that has since changed
    // selects whatever moved into that slot, and track lists change when a
    // stream switches rendition.
    public fun selectQuality(level: QualityLevel?)

    public fun selectAudioTrack(track: AudioTrack)

    // Null is captions off, which is a choice a menu offers as a row.
    public fun selectSubtitleTrack(track: SubtitleTrack?)

    public fun setRate(rate: Float)

    public fun setFullscreen(fullscreen: Boolean)

    // Cleared by whoever showed it. A message that lingers is one a viewer reads
    // as describing what is happening now.
    public fun dismissMessage()
}
