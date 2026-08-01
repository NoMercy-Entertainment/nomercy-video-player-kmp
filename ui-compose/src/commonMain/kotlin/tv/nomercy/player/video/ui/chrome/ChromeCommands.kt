// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.video.Stretching
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
    ChromePresentationCommands,
    ChromeSkipCommands

// Whether openings and endings are skipped or offered.
//
// Its own seam rather than a method on the transport interface: it is not a
// button on the bar, it does not reach the player, and it is the only command
// here whose answer outlives the item being played.
public interface ChromeSkipCommands {

    /**
     * Turn auto-skipping of openings and endings on or off.
     *
     * Writes through to whoever owns the setting rather than storing it: the
     * viewer set this once for their account, not once for this film.
     */
    public fun setAutoSkipChapters(enabled: Boolean)

    /**
     * Switch the right-hand clock between what is left and how long the item is.
     *
     * Here rather than on the transport for the same reason as above: it is a
     * preference the viewer sets once and not a thing that happens to the film.
     * The web persists it under `showRemaining` and defaults it on.
     */
    public fun setShowRemaining(show: Boolean)
}

// What plays, and where in it.
// The width is the web bar's, not a decision made here: every method below is a
// button on it. Splitting this into "transport" and "menus" would let a chrome
// implement half the bar and typecheck, which is the thing the fidelity grade
// caught happening once already.
@Suppress("ComplexInterface")
public interface ChromeTransportCommands {

    // Absolute, in seconds. A scrubber knows where it was dropped, and giving it
    // a relative seam would make it compute a delta from a position that has
    // moved since it read it.
    public fun seekTo(seconds: Double)

    // Relative, and it earns its place beside the absolute one rather than
    // replacing it. A scrubber knows exactly where it was dropped; a double-tap
    // knows only how far to jump, and making it compute a target from a position
    // that has moved since it read it is how a skip lands somewhere else.
    public fun seekBy(deltaSeconds: Float)

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

    // The rest of the web bar's menus, on the same seam and for the same
    // reason: the bar knows a viewer pressed the button and not how much room
    // there is to draw the menu in.
    public fun openQualityMenu()

    public fun openSpeedMenu()

    public fun openPlaylistMenu()

    public fun openSettingsMenu()

    /**
     * The other half of every open above — what a trigger's `collapse` semantics
     * action does, mirroring the web's `aria-expanded` pair on the same buttons.
     * Defaulted to nothing for the same reason [playQueueItem] is: a host that
     * implements this seam itself keeps compiling, and the chrome's own
     * implementation overrides it.
     */
    public fun closeMenu() {
    }

    /**
     * Play a queue item outright, which is what choosing a playlist card means.
     *
     * By id rather than by position, like every other selection on this seam: a
     * queue is reordered and appended to while a menu is open, and an index read
     * when the pane was drawn plays whatever moved into that slot.
     *
     * Defaulted to nothing so a host that implements this interface itself keeps
     * compiling — its playlist rows stay inert until it overrides this, which is
     * a visible gap rather than a broken build. The chrome's own implementation
     * overrides it.
     */
    public fun playQueueItem(id: String) {
    }
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

    // Theater and picture-in-picture are explicit like setPlaying, not
    // toggles: a bar drawn from state that has since changed would toggle to
    // the wrong thing, and the viewer pressed the button they could see.
    public fun setTheater(theater: Boolean)

    public fun setPip(pip: Boolean)

    // Cycles rather than sets, because that is what the web button does: one
    // press moves to the next ratio and the player owns the order. A chrome
    // that picked the next one itself would be a second list to keep in step.
    public fun cycleAspectRatio()

    /**
     * Pick one fitting outright, which is what the web's aspect menu does.
     *
     * Cycling is the button's gesture and choosing is the menu's, and a menu
     * built on cycling would have to press the button up to three times to
     * reach the row somebody tapped.
     */
    public fun setAspectRatio(value: Stretching)

    /**
     * Write the whole subtitle style back, not a patch.
     *
     * The web's `subtitleStyle(partial)` takes a patch and merges, which is the
     * right shape for a dynamic object and the wrong one for a data class that
     * already has `copy`. The caller builds the new style with the one field
     * changed and hands it over whole, so there is no merge to get wrong and
     * reset is the same call with the defaults.
     */
    public fun setSubtitleStyle(style: SubtitleStyle)

    // Cleared by whoever showed it. A message that lingers is one a viewer reads
    // as describing what is happening now.
    public fun dismissMessage()
}
