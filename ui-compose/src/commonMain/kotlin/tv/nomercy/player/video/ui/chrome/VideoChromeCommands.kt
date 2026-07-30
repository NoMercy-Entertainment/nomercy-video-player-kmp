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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.ui.chrome.menus.MenuState
import kotlin.math.abs

// What the chrome's controls do, against an actual player.
//
// Two of these methods do not reach the player at all: opening a menu is the
// chrome's own business, and the bar cannot draw one over itself because it does
// not know how large the surface is. They go back to the assembly instead, which
// is the thing that does.
//
// The width is the ChromeCommands contract, which is four interfaces a widget
// picks from rather than one a host implements from a checklist. Collapsing them
// here to satisfy a counter would put a scrubber, a volume slider and a track
// menu behind one method taking a verb.
@Suppress("TooManyFunctions")
internal class VideoChromeCommands(
    private val player: NMVideoPlayer,
    private val scope: CoroutineScope,
    private val onMenu: (MenuState) -> Unit,
    private val onAutoSkipChange: (Boolean) -> Unit,
    private val onShowRemainingChange: (Boolean) -> Unit,
) : ChromeCommands {

    // Straight back out to the host. The player has no opinion about this and
    // no place to keep it — see AutoSkipPreference.
    override fun setAutoSkipChapters(enabled: Boolean): Unit = onAutoSkipChange(enabled)

    // Also straight back out. The web persists it and this library has no store;
    // a preference kept here would reset every time the chrome remounted.
    override fun setShowRemaining(show: Boolean): Unit = onShowRemainingChange(show)

    override fun seekTo(seconds: Double) {
        scope.launch { player.time(seconds) }
    }

    // Through the player's own forward and rewind, which clamp against a
    // duration that may have arrived since the chrome last read one.
    override fun seekBy(deltaSeconds: Float) {
        val magnitude: Double = abs(deltaSeconds.toDouble())

        scope.launch {
            if (deltaSeconds >= 0f) player.forward(magnitude) else player.rewind(magnitude)
        }
    }

    override fun setPlaying(playing: Boolean) {
        scope.launch { if (playing) player.play() else player.pause() }
    }

    override fun next() {
        scope.launch { player.next() }
    }

    override fun previous() {
        scope.launch { player.previous() }
    }

    override fun openAudioMenu(): Unit = onMenu(MenuState.Audio)

    override fun openSubtitleMenu(): Unit = onMenu(MenuState.Subtitle)

    override fun openQualityMenu(): Unit = onMenu(MenuState.Quality)

    override fun openSpeedMenu(): Unit = onMenu(MenuState.Speed)

    override fun openPlaylistMenu(): Unit = onMenu(MenuState.Playlist)

    override fun openSettingsMenu(): Unit = onMenu(MenuState.Main)

    // Autoplay, because the web's card passes `{ source: 'user', autoplay: true }`
    // and somebody who picked an episode is not asking to have it cued up.
    override fun playQueueItem(id: String) {
        scope.launch { player.item(id, autoplay = true) }
    }

    // Theater and picture-in-picture are the player's own view modes, so they
    // go straight through. The chrome does not remember them; it reads them
    // back off the state, which is what keeps the exit glyph honest when
    // something other than the button changed the mode.
    override fun setTheater(theater: Boolean): Unit = player.theater(theater)

    override fun setPip(pip: Boolean): Unit = player.pip(pip)

    override fun cycleAspectRatio(): Unit = player.cycleAspectRatio()

    override fun setAspectRatio(value: Stretching): Unit = player.aspectRatio(value)

    // Straight through to the player, which holds the style and emits
    // CoreEvents.SubtitleStyle with it. Held privately here it went nowhere: a
    // renderer subscribing to the event never heard, and ChromeState read a
    // default while the menu showed a change.
    override fun setSubtitleStyle(style: SubtitleStyle): Unit = player.subtitleStyle(style)

    override fun setVolume(percent: Int) {
        scope.launch { player.volume(percent) }
    }

    override fun setMuted(muted: Boolean) {
        scope.launch { if (muted) player.mute() else player.unmute() }
    }

    // Null is automatic, which the player accepts as a selection rather than as
    // a missing one.
    override fun selectQuality(level: QualityLevel?): Unit = player.quality(level)

    override fun selectAudioTrack(track: AudioTrack): Unit = player.audioTrack(track)

    override fun selectSubtitleTrack(track: SubtitleTrack?): Unit = player.subtitle(track)

    override fun setRate(rate: Float) {
        scope.launch { player.playbackRate(rate.toDouble()) }
    }

    // Recorded and announced rather than done. A player cannot put itself
    // fullscreen: that belongs to the Activity or the window, and whatever owns
    // one listens for this and then the two agree.
    override fun setFullscreen(fullscreen: Boolean): Unit = player.fullscreen(fullscreen)

    override fun dismissMessage(): Unit = player.clearMessage()
}
