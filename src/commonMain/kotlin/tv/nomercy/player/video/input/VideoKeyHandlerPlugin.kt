// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import tv.nomercy.player.core.device.DeviceCapabilities
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.input.KeyHandlerPlugin
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.core.input.keyCombo
import tv.nomercy.player.core.plugin.PluginManifest

// What every key does, ported group for group from the web handler.
//
// Grouped the same way it is there so the two can be read side by side, because
// the point of this port is that a viewer moving between a browser and a
// television finds the same keys doing the same things. A binding that drifted
// would not be an error anywhere; it would be a key that behaves differently on
// one client.
//
// Open so a platform can override one group. That is how the television handler
// replaces the arrow keys without restating the other forty bindings, and it is
// why the groups are separate methods rather than one long installer: the
// suppression below buys that, and merging them to satisfy a counter would take
// the extension point with it.
@Suppress("TooManyFunctions")
public open class VideoKeyHandlerPlugin(
    protected val commands: PlayerCommands,
    protected val capabilities: DeviceCapabilities,
    nowMs: () -> Long,
) : KeyHandlerPlugin<Unit>(nowMs) {

    public companion object Manifest : PluginManifest {
        override val id: String = "key-handler"
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    override fun addDefaults() {
        addPlaybackKeys()
        addNavigationKeys()
        addVolumeKeys()
        addMediaKeys()
        addModifierSeekKeys()
        addQuickSkipKeys()
        addTrackKeys()
        addChapterKeys()
        addFullscreenKeys()
        addSpeedKeys()
        addFrameAdvanceKey()
        addShowTimeKey()
        addSubtitleSizeKeys()
        addAspectRatioKeys()
        addStopKey()
    }

    protected open fun addPlaybackKeys() {
        bindings.bind(keyCombo(" ")) { commands.transport.togglePlay() }
        bindings.bind(PlayerKey.MediaPlay) { commands.transport.play() }
        bindings.bind(PlayerKey.MediaPause) { commands.transport.pause() }
        bindings.bind(PlayerKey.MediaPlayPause) { commands.transport.togglePlay() }
        bindings.bind(PlayerKey.MediaStop) { commands.transport.stop() }
        bindings.bind(PlayerKey.MediaRewind) { commands.transport.seekBy(-ARROW_SECONDS) }
        bindings.bind(PlayerKey.MediaFastForward) { commands.transport.seekBy(ARROW_SECONDS) }
    }

    // Guarded by the form factor rather than by a string sniff. On a television
    // the arrows move focus, and a build that seeked instead would make the
    // controls unreachable — which is the whole reason the television handler
    // exists as a separate set of bindings.
    protected open fun addNavigationKeys() {
        val notATelevision: () -> Boolean = { capabilities.formFactor != FormFactor.Tv }

        bindings.bind(PlayerKey.Left, enabled = notATelevision) { commands.transport.seekBy(-ARROW_SECONDS) }
        bindings.bind(PlayerKey.Right, enabled = notATelevision) { commands.transport.seekBy(ARROW_SECONDS) }
    }

    // A phone has hardware volume keys and a television sends its volume to the
    // panel, so on both of those the arrows are better spent elsewhere.
    protected open fun addVolumeKeys() {
        val pointerDriven: () -> Boolean = { capabilities.formFactor == FormFactor.Desktop }

        bindings.bind(PlayerKey.Up, enabled = pointerDriven) { commands.volume.volumeUp() }
        bindings.bind(PlayerKey.Down, enabled = pointerDriven) { commands.volume.volumeDown() }
        bindings.bind(keyCombo("m")) { commands.volume.toggleMute() }
    }

    protected open fun addMediaKeys() {
        bindings.bind(PlayerKey.Captions) { commands.presentation.cycleSubtitles() }
        bindings.bind(keyCombo("5")) { commands.presentation.cycleSubtitles() }
        bindings.bind(keyCombo("v")) { commands.presentation.cycleSubtitles() }
        bindings.bind(PlayerKey.AudioTrack) { commands.presentation.cycleAudioTracks() }
        bindings.bind(keyCombo("2")) { commands.presentation.cycleAudioTracks() }
        bindings.bind(keyCombo("b")) { commands.presentation.cycleAudioTracks() }
    }

    // Three magnitudes on the same two keys. A viewer looking for one line of
    // dialogue wants three seconds; one skipping a title sequence wants a
    // minute, and neither wants to press the same key twenty times.
    protected open fun addModifierSeekKeys() {
        bindings.bind(keyCombo(PlayerKey.Left.combo, shift = true)) { commands.transport.seekBy(-NUDGE_SECONDS) }
        bindings.bind(keyCombo(PlayerKey.Right.combo, shift = true)) { commands.transport.seekBy(NUDGE_SECONDS) }
        bindings.bind(keyCombo(PlayerKey.Left.combo, alt = true)) { commands.transport.seekBy(-STEP_SECONDS) }
        bindings.bind(keyCombo(PlayerKey.Right.combo, alt = true)) { commands.transport.seekBy(STEP_SECONDS) }
        bindings.bind(keyCombo(PlayerKey.Left.combo, ctrl = true)) { commands.transport.seekBy(-MINUTE_SECONDS) }
        bindings.bind(keyCombo(PlayerKey.Right.combo, ctrl = true)) { commands.transport.seekBy(MINUTE_SECONDS) }
    }

    // The coloured buttons are the same four jumps as the number keys. They are
    // the only keys on a television remote nothing else uses, and a remote is
    // where skipping an opening actually matters.
    protected open fun addQuickSkipKeys() {
        bindings.bind(keyCombo("3")) { commands.transport.seekBy(SKIP_SHORT) }
        bindings.bind(keyCombo("6")) { commands.transport.seekBy(SKIP_MEDIUM) }
        bindings.bind(keyCombo("9")) { commands.transport.seekBy(SKIP_LONG) }
        bindings.bind(keyCombo("1")) { commands.transport.seekBy(SKIP_LONGEST) }

        bindings.bind(PlayerKey.ColorRed) { commands.transport.seekBy(SKIP_SHORT) }
        bindings.bind(PlayerKey.ColorGreen) { commands.transport.seekBy(SKIP_MEDIUM) }
        bindings.bind(PlayerKey.ColorYellow) { commands.transport.seekBy(SKIP_LONG) }
        bindings.bind(PlayerKey.ColorBlue) { commands.transport.seekBy(SKIP_LONGEST) }
    }

    protected open fun addTrackKeys() {
        bindings.bind(PlayerKey.MediaNext) { commands.transport.next() }
        bindings.bind(PlayerKey.MediaPrevious) { commands.transport.previous() }
        bindings.bind(keyCombo("n")) { commands.transport.next() }
        bindings.bind(keyCombo("p")) { commands.transport.previous() }
    }

    protected open fun addChapterKeys() {
        bindings.bind(keyCombo("n", shift = true)) { commands.transport.nextChapter() }
        bindings.bind(keyCombo("p", shift = true)) { commands.transport.previousChapter() }
    }

    // Two ways in, and one way out.
    //
    // The web's out is Escape. Here it is [PlayerKey.Back], because that is the
    // press: the input adapters map a keyboard's Escape and a remote's back
    // button to the same key, so a binding written against the string "Escape"
    // would sit in the table and never be reached.
    //
    // Guarded on already being fullscreen rather than bound unconditionally,
    // because an unguarded binding CONSUMES the press: a host that uses Escape
    // to close the player would find it stopped working everywhere the picture
    // is not filling the screen. Enabled-false leaves the key with the platform.
    protected open fun addFullscreenKeys() {
        bindings.bind(keyCombo("f")) { commands.window.toggleFullscreen() }
        bindings.bind(keyCombo("F11")) { commands.window.toggleFullscreen() }
        // The toggle under the guard, not a second command for leaving. Reached
        // only while fullscreen is on, where toggling IS leaving — and an
        // interface carrying both a toggle and a one-way exit would be asking
        // every implementor to write the same call twice.
        bindings.bind(
            PlayerKey.Back,
            enabled = { commands.window.isFullscreen() },
        ) { commands.window.toggleFullscreen() }
    }

    // Along the list rather than by multiplying, so the steps are the ones the
    // player offers. Multiplying arrives at rates no engine supports and then
    // fails at the engine rather than here.
    protected open fun addSpeedKeys() {
        bindings.bind(keyCombo("]")) { stepRate(1) }
        bindings.bind(keyCombo("[")) { stepRate(-1) }
        bindings.bind(keyCombo("=")) { applyRate(NORMAL_RATE) }
    }

    // Paused only. Advancing a frame while playing is a seek backwards into a
    // film that has already moved past it.
    protected open fun addFrameAdvanceKey() {
        bindings.bind(keyCombo("e"), enabled = { !commands.state.isPlaying() }) {
            commands.transport.seekBy(ONE_FRAME_AT_30FPS)
        }
    }

    protected open fun addShowTimeKey() {
        bindings.bind(keyCombo("t")) {
            val remaining: Double = (commands.state.duration() - commands.state.time()).coerceAtLeast(0.0)
            commands.presentation.message("${clockOf(commands.state.time())} / -${clockOf(remaining)}")
        }
    }

    // An event rather than a call, because the size of the subtitles belongs to
    // whatever is drawing them. A key handler that set it directly would be one
    // that knows how subtitles are rendered.
    protected open fun addSubtitleSizeKeys() {
        bindings.bind(keyCombo("+")) { commands.presentation.emit(SUBTITLE_SIZE_UP) }
        bindings.bind(keyCombo("+", shift = true)) { commands.presentation.emit(SUBTITLE_SIZE_UP) }
        bindings.bind(keyCombo("-")) { commands.presentation.emit(SUBTITLE_SIZE_DOWN) }
    }

    protected open fun addAspectRatioKeys() {
        bindings.bind(keyCombo("a")) { commands.presentation.cycleAspectRatio() }
        bindings.bind(PlayerKey.Favorites) { commands.presentation.cycleAspectRatio() }
    }

    protected open fun addStopKey() {
        bindings.bind(keyCombo("s")) { commands.transport.stop() }
    }

    private fun stepRate(direction: Int) {
        val rates: List<Float> = commands.presentation.rates()
        val at: Int = rates.indexOf(commands.presentation.rate())
        val next: Int = at + direction

        if (at >= 0 && next in rates.indices) applyRate(rates[next])
    }

    private fun applyRate(rate: Float) {
        commands.presentation.rate(rate)
        commands.presentation.message("${rate}x")
    }
}

// Written here rather than taken from a formatter, because an hour-long film and
// a three-minute song want the same shape and the hours are only shown when
// there are any.
private fun clockOf(seconds: Double): String {
    val whole: Int = seconds.toInt()
    val hours: Int = whole / SECONDS_PER_HOUR
    val minutes: Int = (whole % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val remainder: Int = whole % SECONDS_PER_MINUTE

    return if (hours > 0) {
        "$hours:${pad(minutes)}:${pad(remainder)}"
    } else {
        "$minutes:${pad(remainder)}"
    }
}

private fun pad(value: Int): String = if (value < TWO_DIGITS) "0$value" else "$value"

// The one a bare arrow or a media key moves by.
//
// Five, which is what the web's `rewind()` and `forward()` default to when the
// handler calls them with no argument, and what the shortcuts panel prints
// beside the arrows. Ten is the ALT distance and had been used for both, so the
// panel promised five and the player moved ten.
private const val ARROW_SECONDS = 5f

// Small enough to find a line of dialogue that was missed.
private const val NUDGE_SECONDS = 3f

// What alt buys: twice a bare arrow.
private const val STEP_SECONDS = 10f

private const val MINUTE_SECONDS = 60f

private const val SKIP_SHORT = 30f
private const val SKIP_MEDIUM = 60f
private const val SKIP_LONG = 90f
private const val SKIP_LONGEST = 120f

private const val NORMAL_RATE = 1f

// One frame at thirty a second, which is close enough at every frame rate to
// land on the next picture rather than the same one.
private const val ONE_FRAME_AT_30FPS = 1f / 30f

// Below this a minute or a second needs a leading zero, or 1:5 reads as
// one minute and five minutes.
private const val TWO_DIGITS = 10

private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60

private const val SUBTITLE_SIZE_UP = "subtitle-size-up"
private const val SUBTITLE_SIZE_DOWN = "subtitle-size-down"
