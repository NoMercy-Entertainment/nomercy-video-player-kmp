// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.tv.formatTime
import tv.nomercy.player.video.tv.nextChapterStart
import tv.nomercy.player.video.tv.previousChapterStart
import tv.nomercy.player.video.ui.tv.FluentIcons
import tv.nomercy.player.video.ui.tv.PlayerIconButton
import tv.nomercy.player.video.ui.tv.TvChromeStrings

// The transport row, for a pointer or a finger rather than a remote.
//
// The order below is the web player's, control for control, from
// desktop-ui/helpers/dom.ts buildBottomRow. It is not an arrangement chosen
// here: a chrome with the same controls in a different order is a different
// player to anyone who has used the web one. This row was five controls in an
// order of its own until it was graded against that file, and two of the five
// were not on the web bar at all.
//
// The glyphs are the web's too. FluentIcons carries the same path data the
// browser renders, generated from the same table by
// scripts/generate-player-icons.py rather than redrawn.
@Composable
public fun TransportBar(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    modifier: Modifier = Modifier,
    buttons: ChromeButtons = ChromeButtons(),
    /** `buttonPriority` — which control goes first when the row runs out of room. */
    priority: List<ChromeControl> = CHROME_PRIORITY,
    /** `portraitHidden` — dropped at any width in portrait. */
    portraitHidden: Set<ChromeControl> = CHROME_PORTRAIT_HIDDEN,
    /** `volumeSlider` — whether the level is set on a track or in a popup. */
    volumeSlider: VolumeSliderMode = VolumeSliderMode.Auto,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp: Int = boundedWidthDp(maxWidth)
        val metrics: BarMetrics = remember(widthDp) { barMetricsFor(widthDp) }

        val fits: Set<ChromeControl> = remember(maxWidth, buttons, state, priority) {
            visibleControls(
                widthDp = widthDp,
                contentHidden = { state.lacksContentFor(it) },
                enabled = { buttons.allows(it) },
                priority = priority,
                portraitHidden = portraitHidden,
            ).toSet()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.paddingHorizontal, vertical = metrics.paddingVertical)
                .testTag(TRANSPORT_BAR_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
        ) {
            TransportButtons(state, commands, strings, fits)
            VolumeCluster(state, commands, fits, volumeSpecFor(state, strings, volumeSlider, widthDp))
            TimeReadout(state, commands, buttons)
            ViewButtons(state, commands, strings, fits)
            MenuButtons(state, commands, strings, fits)
        }
    }
}

// Web order 1-7, in three groups because the web bar has three: the play
// button, the two seek steps, and the two chapter jumps. Split along those
// seams rather than at an arbitrary line count, so a control moves with the
// group it belongs to.
@Composable
private fun TransportButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    fits: Set<ChromeControl>,
) {
    PlayPauseAndPrevious(state, commands, strings, fits)
    SeekButtons(commands, strings, fits)
    ChapterButtons(state, commands, strings, fits)
    NextButton(state, commands, strings, fits)
}

// Play leads the row, previous follows it.
@Composable
private fun PlayPauseAndPrevious(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    fits: Set<ChromeControl>,
) {
    if (ChromeControl.PLAY in fits) {
        // The glyph and the label are one decision rather than two conditions
        // that happen to read the same state. Written apart, an edit to one is
        // an edit to half of it — a pause glyph announcing itself as Play, which
        // is invisible to anyone looking at the screen and wrong for everyone
        // who is not.
        val control: TransportControl = if (state.playing) {
            TransportControl(FluentIcons.Pause, strings.pause)
        } else {
            TransportControl(FluentIcons.Play, strings.play)
        }

        PlayerIconButton(
            icon = control.icon,
            description = control.description,
            onClick = { commands.setPlaying(!state.playing) },
            modifier = Modifier.testTag(PLAY_PAUSE_TAG),
        )
    }

    // Drawn on the first item, disabled — the web's setDisabled(prevBtn,
    // onFirst). It used to be hidden there, on the reasoning that a control
    // which does nothing should not be shown; that reads well and it makes the
    // bar jump every time a queue reaches either end.
    if (ChromeControl.PREVIOUS in fits) {
        PlayerIconButton(
            icon = FluentIcons.Previous,
            description = strings.previous,
            enabled = state.hasPrevious,
            onClick = { commands.previous() },
        )
    }

}

// The two ten-second steps.
@Composable
private fun SeekButtons(
    commands: ChromeCommands,
    strings: TvChromeStrings,
    fits: Set<ChromeControl>,
) {
    if (ChromeControl.SEEK_BACK in fits) {
        PlayerIconButton(
            icon = FluentIcons.SeekBack,
            description = strings.seekBack,
            onClick = { commands.seekBy(-SEEK_STEP_SECONDS) },
            modifier = Modifier.testTag(SEEK_BACK_TAG),
        )
    }

    if (ChromeControl.SEEK_FORWARD in fits) {
        PlayerIconButton(
            icon = FluentIcons.SeekForward,
            description = strings.seekForward,
            onClick = { commands.seekBy(SEEK_STEP_SECONDS) },
            modifier = Modifier.testTag(SEEK_FORWARD_TAG),
        )
    }

}

// Only where the item has chapters. An item with none is not a player missing a
// feature, and two dead buttons say otherwise.
@Composable
private fun ChapterButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    fits: Set<ChromeControl>,
) {
    val starts: List<Double> = state.chapters.map { it.startSeconds }

    // Ranked separately by the web, so gated separately here. Drawn as a pair
    // they would appear and disappear together at whichever of the two widths
    // came first, which is one control's rule applied to two controls.
    if (ChromeControl.CHAPTER_PREV in fits) {
        PlayerIconButton(
            icon = FluentIcons.ChapterBack,
            description = strings.chapterBack,
            onClick = { commands.seekTo(previousChapterStart(starts, state.timeSeconds)) },
            modifier = Modifier.testTag(CHAPTER_BACK_TAG),
        )
    }

    if (ChromeControl.CHAPTER_NEXT in fits) {
        PlayerIconButton(
            icon = FluentIcons.ChapterForward,
            description = strings.chapterForward,
            onClick = { nextChapterStart(starts, state.timeSeconds)?.let(commands::seekTo) },
            modifier = Modifier.testTag(CHAPTER_FORWARD_TAG),
        )
    }
}

// Next closes the transport group, after the chapter jumps.
//
// Declared in the order it is drawn, like dom.ts is. That is not tidiness:
// check-chrome-parity.py reads this file top to bottom and compares the glyph
// sequence to the web's builder, so a helper declared out of order reports as a
// bar in the wrong order. Keep declaration order and draw order the same.
@Composable
private fun NextButton(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    fits: Set<ChromeControl>,
) {
    // Drawn at the end of a queue, disabled. Hiding it there reflows the bar
    // and moves every other control under the viewer's finger.
    if (ChromeControl.NEXT in fits) {
        PlayerIconButton(
            icon = FluentIcons.Next,
            description = strings.next,
            enabled = state.hasNext,
            onClick = { commands.next() },
        )
    }
}

// Web order 8. The glyph is the level rather than one speaker: the web has
// volumeHigh, volumeMedium, volumeLow and volumeMuted and picks between them,
// so a viewer reads the level without opening anything.
@Composable
private fun VolumeCluster(
    state: ChromeState,
    commands: ChromeCommands,
    fits: Set<ChromeControl>,
    spec: VolumeSpec,
) {
    if (ChromeControl.MUTE !in fits) return

    // The button AND the way to set the level. This drew the button alone while
    // the responsive arithmetic reserved 96dp beside it for a track nothing put
    // there, so a viewer could mute and could not turn it down.
    VolumeControl(
        state = state,
        commands = commands,
        spec = spec,
        modifier = Modifier.testTag(VOLUME_TAG),
    )
}


// The web's four speaker glyphs, by level. Kept here beside the bar's other icon
// choices and read by the volume control, so the button in the popup and the one
// on the row cannot disagree about which speaker the level is.
internal fun volumeIconFor(state: ChromeState): ImageVector = when {
    state.muted || state.volume == 0 -> FluentIcons.VolumeMuted
    state.volume < VOLUME_LOW -> FluentIcons.VolumeLow
    // At or below, not below. The web's medium arm is `volume <= 60`, and a
    // strict comparison here put the speaker one glyph higher at exactly
    // sixty — the value a slider dragged to a round number lands on.
    state.volume <= VOLUME_MEDIUM -> FluentIcons.VolumeMedium
    else -> FluentIcons.VolumeHigh
}

// Web order 9-11: current time, a divider element, remaining time.
@Composable
private fun RowScope.TimeReadout(
    state: ChromeState,
    commands: ChromeCommands,
    buttons: ChromeButtons,
) {
    if (!buttons.time) {
        // The spacer stays even with the clock off. It is what splits the bar,
        // and a consumer hiding the time must not collapse every control into
        // one left-aligned clump.
        Spacer(modifier = Modifier.weight(1f).widthIn(min = DIVIDER_MIN_WIDTH))
        return
    }

    BasicText(text = formatTime(state.timeSeconds), style = READOUT)

    // THE element that splits this bar, and the one this port read wrong.
    //
    //     .divider { display: flex; flex: 1; min-width: 16px; }
    //
    // A flex spacer that takes every pixel left over, so everything before it
    // sits left and everything after it sits right. Ported here as a 1dp
    // hairline on the reading that the web draws "a real element rather than
    // padding" — which is true of the markup and says nothing about the rule.
    // The result was one clumped row: transport, volume, both clocks and
    // settings bunched against the left edge with the whole right half empty,
    // while the shipped Android player splits at exactly this point.
    Spacer(modifier = Modifier.weight(1f).widthIn(min = DIVIDER_MIN_WIDTH))

    // A BUTTON, as it is on the web: clicking it switches between what is left
    // and how long the item is. It drew what-is-left with no way to reach the
    // other, and drew "-0:00" for the whole of every live stream. See
    // [remainingReadout].
    BasicText(
        text = remainingReadout(state.timeSeconds, state.durationSeconds, state.showRemaining),
        style = READOUT,
        modifier = Modifier.clickable { commands.setShowRemaining(!state.showRemaining) },
    )
}

// Web order 12-15: aspect ratio, theater, picture in picture, speed.
@Composable
private fun ViewButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    fits: Set<ChromeControl>,
) {
    if (ChromeControl.ASPECT_RATIO in fits) {
        PlayerIconButton(
            icon = FluentIcons.AspectFit,
            description = strings.aspectRatio,
            onClick = { commands.cycleAspectRatio() },
            modifier = Modifier.testTag(ASPECT_RATIO_TAG),
        )
    }

    if (ChromeControl.THEATER in fits) {
        PlayerIconButton(
            icon = if (state.theater) FluentIcons.TheaterExit else FluentIcons.Theater,
            description = strings.theater,
            onClick = { commands.setTheater(!state.theater) },
            modifier = Modifier.testTag(THEATER_TAG),
        )
    }

    if (ChromeControl.PIP in fits) {
        PlayerIconButton(
            icon = if (state.pip) FluentIcons.PipExit else FluentIcons.PipEnter,
            description = strings.pictureInPicture,
            onClick = { commands.setPip(!state.pip) },
            modifier = Modifier.testTag(PIP_TAG),
        )
    }

    if (ChromeControl.SPEED in fits) {
        PlayerIconButton(
            icon = FluentIcons.Speed,
            // The rate, when it is not 1. applyRate puts it in the aria-label and
            // this said only "Speed" at every rate — invisible as an a11y gap until
            // tooltips landed and started reading the same string.
            description = speedButtonLabel(strings.speed, state.rate),
            onClick = { commands.openSpeedMenu() },
            modifier = Modifier.testTag(SPEED_TAG),
        )
    }
}

// Web order 16-20: subtitles, audio, quality, playlist, settings.
@Composable
private fun MenuButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    fits: Set<ChromeControl>,
) {
    if (ChromeControl.SUBTITLES in fits) {
        // subtitlesOff when none is on, which is how the web says the difference
        // without a viewer opening the menu to find out.
        PlayerIconButton(
            icon = if (state.activeSubtitle == null) FluentIcons.SubtitlesOff else FluentIcons.Subtitles,
            description = strings.subtitles,
            onClick = { commands.openSubtitleMenu() },
        )
    }

    // Offered only where there is a choice. One audio track is not a menu, it is
    // a row that opens onto itself.
    if (ChromeControl.AUDIO in fits) {
        PlayerIconButton(
            icon = FluentIcons.Language,
            description = strings.language,
            onClick = { commands.openAudioMenu() },
        )
    }

    ListMenuButtons(state, commands, strings, fits)
}

// Quality, playlist and settings — the three that open a list rather than pick
// a track.
@Composable
private fun ListMenuButtons(
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
    fits: Set<ChromeControl>,
) {
    if (ChromeControl.QUALITY in fits) {
        PlayerIconButton(
            icon = FluentIcons.Quality,
            // What is PLAYING, not what was selected. On an adaptive ladder those
            // differ constantly, and announcing the selection says "Auto" forever.
            description = qualityButtonLabel(strings.quality, state.activeQuality?.describe()),
            onClick = { commands.openQualityMenu() },
            modifier = Modifier.testTag(QUALITY_TAG),
        )
    }

    if (ChromeControl.PLAYLIST in fits) {
        PlayerIconButton(
            icon = FluentIcons.Playlist,
            description = strings.playlist,
            onClick = { commands.openPlaylistMenu() },
            modifier = Modifier.testTag(PLAYLIST_TAG),
        )
    }

    if (ChromeControl.SETTINGS in fits) {
        PlayerIconButton(
            icon = FluentIcons.Settings,
            description = strings.settings,
            onClick = { commands.openSettingsMenu() },
            modifier = Modifier.testTag(SETTINGS_TAG),
        )
    }

    // Last in the row, after settings, which is where the web puts it.
    //
    // It was missing entirely: ChromeButtons carried the flag, ChromeState
    // carried the state and ChromeCommands carried setFullscreen, and nothing
    // ever drew the button. Everything was wired except the one part a viewer
    // touches.
    //
    // The glyph swaps rather than a second button appearing, so the control
    // stays in one place whether or not the player is fullscreen.
    if (ChromeControl.FULLSCREEN in fits) {
        PlayerIconButton(
            icon = if (state.fullscreen) FluentIcons.ExitFullscreen else FluentIcons.Fullscreen,
            description = if (state.fullscreen) strings.exitFullscreen else strings.fullscreen,
            onClick = { commands.setFullscreen(!state.fullscreen) },
            modifier = Modifier.testTag(FULLSCREEN_TAG),
        )
    }
}

// A glyph and what it announces itself as, which are the same choice.
private data class TransportControl(val icon: ImageVector, val description: String)

/**
 * Rule 1: whether the consumer asked for this control at all.
 *
 * MUTE and VOLUME both answer to [ChromeButtons.volume] because the web's button
 * map points `mute` at the volume element and `volume` at nothing — the slider
 * is what rank 3 stands for, and a bar that has one has both.
 */
internal fun ChromeButtons.allows(control: ChromeControl): Boolean = when (control) {
    ChromeControl.PLAY -> playPause
    ChromeControl.MUTE, ChromeControl.VOLUME -> volume
    ChromeControl.FULLSCREEN -> fullscreen
    ChromeControl.SETTINGS -> settings
    ChromeControl.NEXT, ChromeControl.PREVIOUS -> previousNext
    ChromeControl.CHAPTER_PREV, ChromeControl.CHAPTER_NEXT -> chapters
    ChromeControl.SEEK_BACK -> seekBack
    ChromeControl.SEEK_FORWARD -> seekForward
    ChromeControl.THEATER -> theater
    ChromeControl.PIP -> pictureInPicture
    ChromeControl.SPEED -> speed
    ChromeControl.QUALITY -> quality
    ChromeControl.SUBTITLES -> subtitles
    ChromeControl.AUDIO -> audio
    ChromeControl.ASPECT_RATIO -> aspectRatio
    ChromeControl.PLAYLIST -> playlist
}

/**
 * Rule 2: whether the item can offer this control.
 *
 * One audio track is not a menu, an item with no chapters is not a player
 * missing a feature, and a queue of one has nothing to list. These cost no width
 * in the accumulation, which is the part that matters — a control nobody can see
 * must not push a later one off the end of the bar.
 */
internal fun ChromeState.lacksContentFor(control: ChromeControl): Boolean = when (control) {
    ChromeControl.CHAPTER_PREV, ChromeControl.CHAPTER_NEXT -> chapters.isEmpty()
    ChromeControl.AUDIO -> audioTracks.size <= 1
    ChromeControl.QUALITY -> qualityLevels.isEmpty()
    ChromeControl.PLAYLIST -> queueSize <= 1
    else -> false
}

/**
 * The width to choose a breakpoint from, when there may not be one.
 *
 * `BoxWithConstraints` hands back `Dp.Infinity` under a parent that scrolls
 * horizontally, and the browser has no equivalent — a CSS container query always
 * has a number to answer with. So the port had a case the oracle cannot teach
 * it, and what it did with that case was an accident of arithmetic:
 * `Float.POSITIVE_INFINITY.toInt()` is `Int.MAX_VALUE`, which lands on the
 * widest band and draws everything. Right answer, reached by not being asked
 * the question.
 *
 * It is written down because the neighbouring accident is not survivable. A
 * constraint that arrives as `NaN` converts to ZERO, which is the narrowest
 * band, and a bar that quietly collapses to one control inside a scrolling
 * container looks like a layout decision rather than a missing width.
 *
 * Unbounded means "as much room as you want", so the widest band is the honest
 * answer as well as the convenient one.
 */


internal const val TRANSPORT_BAR_TAG = "nm-transport-bar"
internal const val PLAY_PAUSE_TAG = "nm-play-pause"
internal const val SEEK_BACK_TAG = "nm-seek-back"
internal const val SEEK_FORWARD_TAG = "nm-seek-forward"
internal const val CHAPTER_BACK_TAG = "nm-chapter-back"
internal const val CHAPTER_FORWARD_TAG = "nm-chapter-forward"
internal const val VOLUME_TAG = "nm-volume"
internal const val ASPECT_RATIO_TAG = "nm-aspect-ratio"
internal const val THEATER_TAG = "nm-theater"
internal const val PIP_TAG = "nm-pip"
internal const val SPEED_TAG = "nm-speed"
internal const val QUALITY_TAG = "nm-quality"
internal const val PLAYLIST_TAG = "nm-playlist"
internal const val FULLSCREEN_TAG: String = "nm-chrome-fullscreen"

internal const val SETTINGS_TAG = "nm-settings"

private val READOUT = TextStyle(color = Color.White)

// The gap and the padding are BarMetrics' now, because the web has four sets of
// them and they are container queries on the player's own width. They were one
// constant each here: a gap of 8 against the web's widest 2, which across
// eighteen controls pushed the whole right-hand group past the edge of the row.
// min-width: 16px, from the web rule.
private val DIVIDER_MIN_WIDTH = 16.dp

// The web's own step, and the number its tooltip says out loud.
private const val SEEK_STEP_SECONDS = 10f

// desktop-ui/helpers/buttonState.ts: `volume < 30` is low and `volume <= 60` is
// medium. These were 33 and 66, which is the same idea and not the same
// picture: a volume of 62 drew medium here and high in a browser.
internal const val VOLUME_LOW = 30
internal const val VOLUME_MEDIUM = 60
