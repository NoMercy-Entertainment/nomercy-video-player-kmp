// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.video.ui.rememberDeviceCapabilities
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.tv.formatTime
import tv.nomercy.player.video.tv.nextChapterStart
import tv.nomercy.player.video.tv.previousChapterStart
import tv.nomercy.player.video.ui.chrome.menus.MenuState
import tv.nomercy.player.video.ui.tv.FluentIcons
import tv.nomercy.player.video.ui.tv.PlayerIconButton
import tv.nomercy.player.video.ui.tv.TvChromeStrings

// What still fits on the row, with the one input the web reads and this never did.
//
// `state.isNoHover` there comes from `matchMedia('(hover: none)')`. Here it was
// never passed at all, so it took its `false` default on every surface and the
// row reserved 96dp beside mute for a volume slider that expands on hover — on a
// phone, where nothing can hover and it never expands. At 396dp that is exactly
// the room fullscreen and settings needed: two default-ON controls, ranked third
// and fourth, missing from every touch build.
@Composable
private fun rememberFittingControls(
    widthDp: Int,
    buttons: ChromeButtons,
    state: ChromeState,
    drop: DropOrder,
): Set<ChromeControl> {
    val noHover: Boolean = rememberDeviceCapabilities().hasTouch

    return remember(widthDp, buttons, state, drop, noHover) {
        visibleControls(
            widthDp = widthDp,
            noHover = noHover,
            contentHidden = { state.lacksContentFor(it) },
            enabled = { buttons.allows(it) },
            priority = drop.priority,
            portraitHidden = drop.portraitHidden,
        ).toSet()
    }
}

// What goes first when the row runs out of room, and what never survives
// portrait. One value because they are one decision and they travel together.
private data class DropOrder(
    val priority: List<ChromeControl>,
    val portraitHidden: Set<ChromeControl>,
)

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
    /**
     * `buttonOrder` — a consumer's visual override.
     *
     * Every control named here is re-anchored to the END of the row in the given
     * sequence, and anything unnamed keeps its natural position. That is the
     * web's rule exactly: `applyButtonOrder` appends each named button to the
     * row, so a named control crosses the divider and lands after fullscreen.
     *
     * Independent of [priority], which decides what is DROPPED as the row
     * narrows. Reordering the bar and changing what survives a phone are
     * different decisions and the web keeps them apart.
     */
    buttonOrder: List<ChromeControl> = emptyList(),
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp: Int = boundedWidthDp(maxWidth)
        val metrics: BarMetrics = remember(widthDp) { barMetricsFor(widthDp) }

        val fits: Set<ChromeControl> = rememberFittingControls(
            widthDp, buttons, state, DropOrder(priority, portraitHidden),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // `.bottom-row { height: 40px; padding: 4px 16px }` — forty
                // TOTAL, with the padding inside it and the 40dp controls
                // overflowing by four top and bottom.
                //
                // Compose adds padding to its content instead, so a row of 40dp
                // buttons with 4dp of vertical padding measured 48 — eight
                // taller than the browser's, which pushed the progress strip
                // that much further from the controls. The height has to be
                // stated for the padding to be absorbed rather than added.
                .height(ROW_HEIGHT)
                .padding(horizontal = metrics.paddingHorizontal, vertical = metrics.paddingVertical)
                .testTag(TRANSPORT_BAR_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
        ) {
            // What the consumer moved, and what is left where the web puts it.
            //
            // Filtered by [fits] first: a control the row has no room for is not
            // drawn because it was named in an order, and the web agrees — its
            // resize pass runs over whatever the DOM holds, whatever order it is
            // in.
            val moved: List<ChromeControl> = buttonOrder.filter { it in fits }.distinct()
            val natural: Set<ChromeControl> = fits - moved.toSet()
            val spec: VolumeSpec = volumeSpecFor(state, strings, volumeSlider, widthDp)

            TransportButtons(state, commands, strings, natural)
            VolumeCluster(state, commands, natural, spec)
            TimeReadout(state, commands, buttons, metrics)
            ViewButtons(state, commands, strings, natural)

            // The tail, in the sequence the consumer asked for.
            moved.forEach { control ->
                if (control == ChromeControl.MUTE) {
                    // The cluster rather than the button: the level has to travel
                    // with the thing that mutes it, or a reordered bar mutes in
                    // one place and sets the volume in another.
                    VolumeCluster(state, commands, setOf(ChromeControl.MUTE), spec)
                } else {
                    ControlButton(control, state, commands, strings)
                }
            }
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
    TRANSPORT_ORDER.forEach { control ->
        if (control in fits) TransportControlButton(control, state, commands, strings)
    }
}

// Web order 1-7: play, previous, the two ten-second steps, the two chapter
// jumps, next.
private val TRANSPORT_ORDER: List<ChromeControl> = listOf(
    ChromeControl.PLAY,
    ChromeControl.PREVIOUS,
    ChromeControl.SEEK_BACK,
    ChromeControl.SEEK_FORWARD,
    ChromeControl.CHAPTER_PREV,
    ChromeControl.CHAPTER_NEXT,
    ChromeControl.NEXT,
)

// One control, drawn.
//
// A `when` rather than a run of composables that each re-test the same set,
// because buttonOrder needs a control drawn OUT of its group — and the
// alternative, a second copy of these eighteen blocks for the reordered tail,
// is the duplicate-declaration defect this codebase keeps finding.
//
// The branches are in draw order and that is load-bearing:
// check-chrome-parity.py reads every glyph reference in this file top to bottom
// and compares the sequence to the web's builder, so a branch out of order
// reports as a bar in the wrong order. Split in two around the volume glyphs,
// which volumeIconFor names between the transport group and the view group.
//
// Naming a glyph in prose here counts as drawing one, because that reader is a
// regex over the whole file — which is how this comment reddened the gate the
// first time it was written.
@Composable
private fun TransportControlButton(
    control: ChromeControl,
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
) {
    val starts: List<Double> = state.chapters.map { it.startSeconds }

    when (control) {
        // The glyph and the label are one decision rather than two conditions
        // that happen to read the same state. Written apart, an edit to one is
        // an edit to half of it — a pause glyph announcing itself as Play, which
        // is invisible to anyone looking at the screen and wrong for everyone
        // who is not.
        ChromeControl.PLAY -> {
            val transport: TransportControl = if (state.playing) {
                TransportControl(FluentIcons.Pause, strings.pause)
            } else {
                TransportControl(FluentIcons.Play, strings.play)
            }

            PlayerIconButton(
                icon = transport.icon,
                description = transport.description,
                onClick = { commands.setPlaying(!state.playing) },
                modifier = Modifier.testTag(PLAY_PAUSE_TAG),
            )
        }

        // Drawn on the first item, disabled — the web's setDisabled(prevBtn,
        // onFirst). It used to be hidden there, on the reasoning that a control
        // which does nothing should not be shown; that reads well and it makes
        // the bar jump every time a queue reaches either end.
        ChromeControl.PREVIOUS -> PlayerIconButton(
            icon = FluentIcons.Previous,
            description = strings.previous,
            enabled = state.hasPrevious,
            onClick = { commands.previous() },
        )

        else -> StepControlButton(control, state, commands, strings)
    }
}

// The steps and the jumps, split from the two above only because detekt measures
// a `when` by its length. The seam is the web's own — dom.ts builds the play
// pair, then the seek pair, then the chapter pair — so it is where a group would
// have been split anyway.
@Composable
private fun StepControlButton(
    control: ChromeControl,
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
) {
    val starts: List<Double> = state.chapters.map { it.startSeconds }

    when (control) {
        ChromeControl.SEEK_BACK -> PlayerIconButton(
            icon = FluentIcons.SeekBack,
            description = strings.seekBack,
            onClick = { commands.seekBy(-SEEK_STEP_SECONDS) },
            modifier = Modifier.testTag(SEEK_BACK_TAG),
        )

        ChromeControl.SEEK_FORWARD -> PlayerIconButton(
            icon = FluentIcons.SeekForward,
            description = strings.seekForward,
            onClick = { commands.seekBy(SEEK_STEP_SECONDS) },
            modifier = Modifier.testTag(SEEK_FORWARD_TAG),
        )

        // Ranked separately by the web, so gated separately here. Drawn as a
        // pair they would appear and disappear together at whichever of the two
        // widths came first, which is one control's rule applied to two.
        ChromeControl.CHAPTER_PREV -> PlayerIconButton(
            icon = FluentIcons.ChapterBack,
            description = strings.chapterBack,
            onClick = { commands.seekTo(previousChapterStart(starts, state.timeSeconds)) },
            modifier = Modifier.testTag(CHAPTER_BACK_TAG),
        )

        ChromeControl.CHAPTER_NEXT -> PlayerIconButton(
            icon = FluentIcons.ChapterForward,
            description = strings.chapterForward,
            onClick = { nextChapterStart(starts, state.timeSeconds)?.let(commands::seekTo) },
            modifier = Modifier.testTag(CHAPTER_FORWARD_TAG),
        )

        // Drawn at the end of a queue, disabled. Hiding it there reflows the bar
        // and moves every other control under the viewer's finger.
        ChromeControl.NEXT -> PlayerIconButton(
            icon = FluentIcons.Next,
            description = strings.next,
            enabled = state.hasNext,
            onClick = { commands.next() },
        )

        else -> Unit
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
    metrics: BarMetrics,
) {
    val readout: TextStyle = remember(metrics) { READOUT.copy(fontSize = metrics.timeFontSize) }

    if (!buttons.time) {
        // The spacer stays even with the clock off. It is what splits the bar,
        // and a consumer hiding the time must not collapse every control into
        // one left-aligned clump.
        Spacer(modifier = Modifier.weight(1f).widthIn(min = DIVIDER_MIN_WIDTH))
        return
    }

    // `.current-time { margin-left: 8px }` — the clock does not sit hard against
    // the volume button, and the row's 2px gap is not that space.
    BasicText(
        text = formatTime(state.timeSeconds),
        style = readout,
        modifier = Modifier.padding(start = TIME_EDGE_MARGIN),
    )

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
    // Gone below 480, where the web hides it outright. The elapsed time stays:
    // a viewer can work out what is left from it, and cannot work out where they
    // are from the other one.
    if (!metrics.showsRemaining) return

    // A pill rather than bare text, which is how the web says it can be pressed
    // before anyone presses it: `border-radius: 4px; padding: 0 4px`, and a
    // background that appears under the pointer.
    BasicText(
        text = remainingReadout(state.timeSeconds, state.durationSeconds, state.showRemaining),
        style = readout,
        modifier = Modifier
            .padding(end = TIME_EDGE_MARGIN)
            .clip(RoundedCornerShape(REMAINING_RADIUS))
            .clickable { commands.setShowRemaining(!state.showRemaining) }
            .padding(horizontal = REMAINING_PADDING),
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
    RIGHT_ORDER.forEach { control ->
        if (control in fits) RightControlButton(control, state, commands, strings)
    }
}

// Web order 9-20: the view toggles, then the menus, then fullscreen.
private val RIGHT_ORDER: List<ChromeControl> = listOf(
    ChromeControl.ASPECT_RATIO,
    ChromeControl.THEATER,
    ChromeControl.PIP,
    ChromeControl.SPEED,
    ChromeControl.SUBTITLES,
    ChromeControl.AUDIO,
    ChromeControl.QUALITY,
    ChromeControl.PLAYLIST,
    ChromeControl.SETTINGS,
    ChromeControl.FULLSCREEN,
)

// The other half of the dispatcher, after the volume glyphs, so the sequence
// check-chrome-parity.py reads out of this file is still the web's.
@Composable
private fun RightControlButton(
    control: ChromeControl,
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
) {
    when (control) {
        ChromeControl.ASPECT_RATIO -> PlayerIconButton(
            icon = FluentIcons.AspectFit,
            description = strings.aspectRatio,
            onClick = { commands.cycleAspectRatio() },
            modifier = Modifier.testTag(ASPECT_RATIO_TAG),
        )

        ChromeControl.THEATER -> PlayerIconButton(
            icon = if (state.theater) FluentIcons.TheaterExit else FluentIcons.Theater,
            description = strings.theater,
            onClick = { commands.setTheater(!state.theater) },
            modifier = Modifier.testTag(THEATER_TAG),
        )

        ChromeControl.PIP -> PlayerIconButton(
            icon = if (state.pip) FluentIcons.PipExit else FluentIcons.PipEnter,
            description = strings.pictureInPicture,
            onClick = { commands.setPip(!state.pip) },
            modifier = Modifier.testTag(PIP_TAG),
        )

        ChromeControl.SPEED -> MenuTriggerButton(
            MenuTriggerSpec(
                icon = FluentIcons.Speed,
                // The rate, when it is not 1. applyRate puts it in the aria-label and
                // this said only "Speed" at every rate — invisible as an a11y gap until
                // tooltips landed and started reading the same string.
                description = speedButtonLabel(strings.speed, state.rate),
                expanded = state.menu == MenuState.Speed,
                open = commands::openSpeedMenu,
                tag = SPEED_TAG,
            ),
            close = commands::closeMenu,
        )

        else -> MenuControlButton(control, state, commands, strings)
    }
}

// The five that open something, and fullscreen after them. Split from the view
// toggles at the web's own seam: dom.ts builds the toggles, then the menu
// triggers, then the fullscreen button last.
@Composable
private fun MenuControlButton(
    control: ChromeControl,
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
) {
    when (control) {
        // subtitlesOff when none is on, which is how the web says the difference
        // without a viewer opening the menu to find out.
        ChromeControl.SUBTITLES -> MenuTriggerButton(
            MenuTriggerSpec(
                icon = if (state.activeSubtitle == null) FluentIcons.SubtitlesOff else FluentIcons.Subtitles,
                description = strings.subtitles,
                expanded = state.menu == MenuState.Subtitle,
                open = commands::openSubtitleMenu,
            ),
            close = commands::closeMenu,
        )

        // Offered only where there is a choice. One audio track is not a menu,
        // it is a row that opens onto itself.
        ChromeControl.AUDIO -> MenuTriggerButton(
            MenuTriggerSpec(
                icon = FluentIcons.Language,
                description = strings.language,
                expanded = state.menu == MenuState.Audio,
                open = commands::openAudioMenu,
            ),
            close = commands::closeMenu,
        )

        else -> ListControlButton(control, state, commands, strings)
    }
}

// Quality, playlist and settings — the three that open a list rather than pick a
// track — and fullscreen last. The same seam the file had before the dispatcher,
// when these were ListMenuButtons and FullscreenButton.
@Composable
private fun ListControlButton(
    control: ChromeControl,
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
) {
    when (control) {
        ChromeControl.QUALITY -> MenuTriggerButton(
            MenuTriggerSpec(
                icon = FluentIcons.Quality,
                // What is PLAYING, not what was selected. On an adaptive ladder those
                // differ constantly, and announcing the selection says "Auto" forever.
                description = qualityButtonLabel(strings.quality, state.activeQuality?.describe()),
                expanded = state.menu == MenuState.Quality,
                open = commands::openQualityMenu,
                tag = QUALITY_TAG,
            ),
            close = commands::closeMenu,
        )

        ChromeControl.PLAYLIST -> MenuTriggerButton(
            MenuTriggerSpec(
                icon = FluentIcons.Playlist,
                description = strings.playlist,
                expanded = state.menu == MenuState.Playlist,
                open = commands::openPlaylistMenu,
                tag = PLAYLIST_TAG,
            ),
            close = commands::closeMenu,
        )

        // Expanded for the MAIN pane — `setMenuTriggerExpanded(null, …)` maps the
        // settings button to the top-level list, and each sub-pane to its own
        // trigger above.
        ChromeControl.SETTINGS -> MenuTriggerButton(
            MenuTriggerSpec(
                icon = FluentIcons.Settings,
                description = strings.settings,
                expanded = state.menu == MenuState.Main,
                open = commands::openSettingsMenu,
                tag = SETTINGS_TAG,
            ),
            close = commands::closeMenu,
        )

        else -> FullscreenControlButton(control, state, commands, strings)
    }
}

// Last in the row, after settings, which is where the web puts it. It was
// missing entirely once: ChromeButtons carried the flag, ChromeState the state
// and ChromeCommands setFullscreen, and nothing drew the button.
//
// The glyph swaps rather than a second button appearing, so the control stays in
// one place whether or not the player is fullscreen.
@Composable
private fun FullscreenControlButton(
    control: ChromeControl,
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
) {
    if (control != ChromeControl.FULLSCREEN) return

    PlayerIconButton(
        icon = if (state.fullscreen) FluentIcons.ExitFullscreen else FluentIcons.Fullscreen,
        description = if (state.fullscreen) strings.exitFullscreen else strings.fullscreen,
        onClick = { commands.setFullscreen(!state.fullscreen) },
        modifier = Modifier.testTag(FULLSCREEN_TAG),
    )
}

// Either half, for a control the consumer moved out of its group.
@Composable
private fun ControlButton(
    control: ChromeControl,
    state: ChromeState,
    commands: ChromeCommands,
    strings: TvChromeStrings,
) {
    if (control in TRANSPORT_ORDER) {
        TransportControlButton(control, state, commands, strings)
    } else {
        RightControlButton(control, state, commands, strings)
    }
}

// A glyph and what it announces itself as, which are the same choice.
private data class TransportControl(val icon: ImageVector, val description: String)

// Rules 1 and 2 of the visibility composition — `allows` and `lacksContentFor`
// — live in ChromeResponsive.kt with the rest of the rules they compose with.

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

// `.time { font-family: ui-monospace…; color: rgb(221,221,221) }`, and the size
// arrives per container band because the web shrinks it below 720.
//
// This was `TextStyle(color = Color.White)`: the default family at the default
// size in the wrong colour. Proportional digits are the part that shows without
// a browser beside it — a 1 is narrower than a 0, so the whole row twitches once
// a second while the clock ticks.
// `.bottom-row { height: 40px }`, which is the same forty as the buttons and a
// different decision — a consumer shrinking the controls does not shrink the row
// they sit in.
private val ROW_HEIGHT: Dp = 40.dp

private val TIME_COLOR: Color = Color(0xFFDDDDDD)

private val READOUT = TextStyle(
    color = TIME_COLOR,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
)

// `.current-time { margin-left: 8px }` and `.remaining-time { margin-right: 8px }`.
private val TIME_EDGE_MARGIN: Dp = 8.dp

// `.remaining-time { border-radius: 4px; padding: 0 4px }`.
private val REMAINING_RADIUS: Dp = 4.dp
private val REMAINING_PADDING: Dp = 4.dp

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
