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
import tv.nomercy.player.video.tv.TvChapter
import tv.nomercy.player.video.tv.TvChromeItem

// Everything the chrome draws from, in one value.
//
// A projection rather than the player's own state. The player knows about
// phases, buffer frontiers and engine errors; a control bar needs to know
// whether to draw a pause glyph. Passing the whole thing would let any widget
// reach anything, and the widgets would then be untestable without a player.
//
// Every field is something visible on screen. A field nothing draws is a field
// that goes stale without anyone noticing.
public data class ChromeState(
    val playing: Boolean = false,
    val buffering: Boolean = false,

    val timeSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    // A fraction rather than seconds, because that is what a bar draws and
    // converting at each widget is three chances to divide by a zero duration.
    val bufferedFraction: Float = 0f,

    val volume: Int = 100,
    val muted: Boolean = false,

    val chapters: List<TvChapter> = emptyList(),

    // The lists and what is chosen from each. Held together rather than as a
    // list plus an index, because an index into a list that has since changed is
    // the wrong track shown as current.
    val qualityLevels: List<QualityLevel> = emptyList(),
    val activeQuality: QualityLevel? = null,
    val audioTracks: List<AudioTrack> = emptyList(),
    val activeAudio: AudioTrack? = null,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    // Null is captions off, which is a choice rather than an absence and is why
    // it is nullable rather than an empty label.
    val activeSubtitle: SubtitleTrack? = null,

    val rate: Float = 1f,

    val item: TvChromeItem? = null,
    // The queue itself, not only its length. The playlist menu draws a row
    // per item and decides between a flat list and a seasons rail from the
    // items' own season and type fields, so a count cannot answer it.
    val queue: List<TvChromeItem> = emptyList(),
    val queueSize: Int = 0,
    val queueIndex: Int = 0,

    val fullscreen: Boolean = false,
    // Theater and picture-in-picture, so their buttons can draw the exit glyph
    // the web draws when each is active rather than the same one both ways.
    val theater: Boolean = false,
    val pip: Boolean = false,

    // Which of the four fittings is in effect, so the aspect menu can mark one
    // and the bar's glyph can follow it. The chrome could only cycle before,
    // which is a button and not a menu: a viewer could change the picture and
    // never be told what it had changed to.
    val aspectRatio: Stretching = Stretching.Uniform,

    // How subtitles are drawn. Carried so the settings pane can mark what each
    // row currently reads, which is the whole difference between a list of
    // options and a list of settings.
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),

    // What the viewer was last told. Cleared by whoever showed it, because a
    // message that lingers is one somebody reads as current.
    val message: String? = null,

    // What went wrong, if anything. Separate from [message] because they are
    // different things with different lifetimes: a message is something the
    // player chose to say and clears itself, and this is a failure that stays
    // until the item is reloaded. Drawing one as the other put a fatal decode
    // error on screen for three seconds and then hid it.
    val error: ChromeError? = null,

    // Whether openings and endings are skipped for the viewer rather than
    // offered to them. Not a player capability and not persisted here — the app
    // owns it, and this is what it currently reads. See [AutoSkipPreference].
    val autoSkipChapters: Boolean = false,
) {

    // Drawn by the bar and by the scrubber, so it is computed once here. Zero
    // rather than a division by zero, which is every live stream: the duration
    // is unknown and a bar has nothing to fill against.
    public val progress: Float
        get() = if (durationSeconds <= 0.0) 0f else (timeSeconds / durationSeconds).coerceIn(0.0, 1.0).toFloat()

    public val hasNext: Boolean get() = queueIndex < queueSize - 1

    public val hasPrevious: Boolean get() = queueIndex > 0
}
