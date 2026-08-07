// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.video.ui.tv.PlayerIconButton
import tv.nomercy.player.video.ui.tv.FluentIcons
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import tv.nomercy.player.video.ui.chrome.menus.MenuState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle

// What is drawn over the picture rather than in a bar, and what all of it has in
// common: none of it fades with the chrome.
//
// A buffering line, whatever the player last said, the skip prompt and a failure
// each answer a question a viewer has RIGHT NOW, and four idle seconds does not
// make any of them less true. Keeping them together says that once instead of
// four times.

// The two lines that sit in the middle of the picture, both OUTSIDE the
// visibility gate.
//
// A film that is still buffering has to say so whether or not the controls are
// up: a viewer looking at a frozen frame with nothing on it cannot tell it from
// a crash. The same goes for whatever the player last said. Neither is part of
// the chrome that fades after four idle seconds, which is why they are drawn
// here rather than inside it.
@Composable
internal fun BoxScope.ChromeStatusText(scene: ChromeScene) {
    if (scene.state.buffering) {
        BufferingSpinner(Modifier.align(Alignment.Center))
    }

    scene.state.message?.let { text -> PlayerMessage(text) }
}

/**
 * The notice band: loading, buffering, a failure, or whatever the host asked for.
 *
 * `.player-message`, which this drew as bare white text in the middle of the
 * picture. The web puts it in a pill near the TOP — `top: 48px` — for a reason
 * worth keeping: centred, it sits exactly where the film is, so every notice
 * reads as part of the picture and covers the thing the viewer is waiting to see.
 */
@Composable
private fun BoxScope.PlayerMessage(text: String) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = MESSAGE_TOP)
            // `max-width: 80%`, so a long failure wraps inside the picture rather
            // than running off both edges of it.
            .fillMaxWidth(MESSAGE_MAX_WIDTH_SHARE)
            .wrapContentWidth()
            .background(MESSAGE_BACKGROUND, RoundedCornerShape(MESSAGE_RADIUS))
            .padding(horizontal = MESSAGE_PADDING_HORIZONTAL, vertical = MESSAGE_PADDING_VERTICAL)
            .testTag(MESSAGE_TAG),
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = Color.White, fontSize = MESSAGE_TEXT_SIZE),
        )
    }
}

// `top: 48px`, `padding: 8px 16px`, `border-radius: 8px`, `max-width: 80%`,
// `background: rgba(20, 22, 30, 0.85)`, `font-size: 0.875rem`.
private val MESSAGE_TOP: Dp = 48.dp
private val MESSAGE_RADIUS: Dp = 8.dp
private val MESSAGE_PADDING_HORIZONTAL: Dp = 16.dp
private val MESSAGE_PADDING_VERTICAL: Dp = 8.dp
private const val MESSAGE_MAX_WIDTH_SHARE = 0.8f
private val MESSAGE_BACKGROUND: Color = Color(red = 20, green = 22, blue = 30, alpha = 217)
private val MESSAGE_TEXT_SIZE = 14.sp

// The layers that sit over the picture and are not the controls: the skip
// prompt and the failure. Split from ChromeLayers because that function is the
// assembly and this is what is layered on it, and together they are longer than
// one function is allowed to be.
@Composable
internal fun BoxScope.ChromeOverlays(scene: ChromeScene) {
    // The big play button, in the middle of the picture, once, on desktop.
    //
    // The reference builds one — `center-btn` with the bigPlay glyph — in its
    // desktop-ui plugin, and this chrome had nothing there. It is the first
    // control a viewer looks for on a film that has not started, and its
    // absence was invisible to every count: it is not in the chrome's control
    // list, so a controls report reads 19/19 without it, and an overlay report
    // only sees it on a page measured before playback begins.
    //
    // Desktop only, because a phone gets touch zones instead — drawn on both it
    // covers the centre gesture a phone owns, which is a working behaviour made
    // worse by adding a control.
    //
    // A ONE-SHOT. The reference's own note: "visible until the user clicks it
    // (or any touch zone triggers play). Once dismissed it stays hidden and the
    // touch zones own play/pause."
    // Dismissed in an EFFECT, never in the composable body.
    //
    // `if (playing) offered = false` written inline writes a MutableState
    // DURING composition, which schedules another composition, which writes
    // again — an infinite recomposition loop. The window then reports
    // Responding=True and paints nothing at all: no heading, no tabs, no
    // picture, while the player's own coroutines keep ticking. That is what a
    // recomposition loop looks like from outside, and it is indistinguishable
    // from a dead renderer until you notice the process is healthy.
    var offered: Boolean by remember { mutableStateOf(true) }
    LaunchedEffect(scene.state.playing) {
        if (scene.state.playing) offered = false
    }

    if (offersCentrePlay(scene, offered)) {
        PlayerIconButton(
            icon = FluentIcons.BigPlay,
            description = scene.strings.play,
            onClick = {
                offered = false
                scene.commands.setPlaying(true)
            },
            modifier = Modifier
                .align(Alignment.Center)
                .size(CENTER_BUTTON_SIZE)
                .testTag(CENTER_PLAY_TAG),
        )
    }

        // The skip prompt, on the chapter the film is actually inside.
    //
    // Bottom-start for an opening and bottom-end for an ending, as his does,
    // so the two never appear in the same place and a viewer learns where to
    // look. Gated on a real chapter list: an item without chapters offers
    // nothing, which is most films.
    // Not while a pane is open, which is the same rule the tooltips follow.
    //
    // The prompt is bottom-anchored and a menu grows up from the bar into
    // exactly that space, so "Skip outro" drew ON TOP of the settings rows —
    // over the row a viewer was reaching for. A menu is a deliberate act and the
    // prompt is an offer; the offer waits.
    skipOffer(scene.state)
        ?.takeIf { !scene.state.autoSkipChapters && !LocalMenuOpen.current }
        ?.let { offer ->
        SkipButton(
            kind = offer,
            label = if (offer == SkipKind.Intro) scene.strings.skipIntro else scene.strings.skipOutro,
            // To the end of the chapter being skipped, which is where the
            // next one starts. seekTo rather than a chapter-forward command,
            // because the boundary is already known here and asking the
            // player to find it again is a second answer that can differ.
            onSkip = { skipTargetOf(scene.state)?.let(scene.commands::seekTo) },
            // Above the time bar, not on it.
            //
            // This aligned to the chrome's bottom edge, which is the edge the
            // strip and the transport row already occupy — so the prompt landed
            // on top of the controls and covered them. Lifted by the stack's own
            // height, summed from the constants that draw it.
            //
            // Padding rather than a move into the control column: the prompt is
            // drawn outside the visibility gate on purpose, and a viewer with the
            // controls hidden still needs the offer to skip.
            modifier = Modifier
                .align(if (offer == SkipKind.Intro) Alignment.BottomStart else Alignment.BottomEnd)
                // The stack itself, plus whatever the stack is inset by — they
                // move together or the prompt lands back on the controls the
                // moment a phone has a gesture bar.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = BOTTOM_STACK_HEIGHT),
        )
    }

    // Over the controls, not instead of them, which is what his overlay
    // does: a viewer reading why playback failed still needs the way out and
    // the playlist to pick something else. Outside the visibility gate for
    // the same reason the buffering line is — a failure that hides itself
    // after four seconds of no pointer movement is a failure nobody read.
    scene.state.error?.let { failure ->
        ChromeErrorOverlay(failure, Modifier.align(Alignment.Center))
    }
}

// `.menu-frame-dialog` — inset 0 over the whole player, with pointer events only
// while a menu is open.
//
// The web closes on any document click whose target is not inside `.menu-frame`.
// There was nothing here at all: a menu opened and the only way out was the close
// button in its own header, so a tap on the picture went to the tap zones
// underneath and seeked the film while the menu the viewer was trying to dismiss
// stayed up.
@Composable
internal fun BoxScope.MenuDismissLayer(menu: MenuState, onMenuChange: (MenuState) -> Unit) {
    if (menu == MenuState.Hidden) return

    Box(
        modifier = Modifier
            .matchParentSize()
            .testTag(MENU_DISMISS_TAG)
            .pointerInput(Unit) { detectTapGestures { onMenuChange(MenuState.Hidden) } },
    )
}

internal const val MENU_DISMISS_TAG = "nm-menu-dismiss"

/**
 * What a click on the picture does when a pointer is driving.
 *
 * The tap zones are the touch build's, and everything about clicking the picture
 * sat inside `if (!pointerDriven)` — so on a desktop a click on the film did
 * nothing at all and a double click did nothing either, while the same two
 * gestures in a browser pause it and fill the screen. There was no layer there
 * to press.
 *
 * A single click toggles play and a double click toggles fullscreen, which is
 * the browser's pairing. Compose delivers both from one detector, so the first
 * click of a double is not also a pause.
 */
@Composable
internal fun BoxScope.PointerClickLayer(
    state: ChromeState,
    commands: ChromeCommands,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .matchParentSize()
            .testTag(POINTER_CLICK_TAG)
            .pointerInput(state.playing, state.fullscreen) {
                detectTapGestures(
                    onTap = {
                        onActivity()
                        commands.setPlaying(!state.playing)
                    },
                    onDoubleTap = {
                        onActivity()
                        commands.setFullscreen(!state.fullscreen)
                    },
                )
            },
    )
}

internal const val POINTER_CLICK_TAG = "nm-pointer-click"

// The pointer build's half of the picture layer, taking the whole input rather
// than three of its fields. It lives beside the layer it mounts because
// VideoChrome.kt is at its file-function ceiling, and a wrapper belongs with
// the thing it wraps rather than with the branch that chose it.
@Composable
internal fun BoxScope.PointerLayer(input: ChromeInput) {
    PointerClickLayer(
        state = input.zones.state,
        commands = input.zones.commands,
        onActivity = input.controller::bumpActivity,
    )
}

/**
 * The buffering ring, which is what the web draws and this drew as a word.
 *
 * `<svg viewBox="0 0 50 50"><circle r="20" stroke="#fff" stroke-width="4"
 * stroke-linecap="round" stroke-dasharray="100 28"/></svg>` in a 72px box,
 * turning once every 0.9s. The dash leaves a gap of 28 against a circumference
 * of about 126, so a little over four fifths of the ring is drawn — the gap is
 * what makes the rotation readable.
 *
 * A centred line of text was neither the web's control nor a good one: it landed
 * in the middle of the picture and repeated whatever the message pill above it
 * already said, so a viewer waiting for a stream saw the same word twice in two
 * different places.
 */
@Composable
private fun BufferingSpinner(modifier: Modifier = Modifier) {
    val turn = rememberInfiniteTransition(label = "buffering")
    val angle: Float by turn.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)),
        label = "angle",
    )

    Canvas(modifier = modifier.size(SPINNER_SIZE).testTag(BUFFERING_TAG)) {
        val stroke: Float = SPINNER_STROKE_SHARE * size.minDimension
        val inset: Float = stroke / 2 + (size.minDimension - SPINNER_RING_SHARE * size.minDimension) / 2

        rotate(angle) {
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = SPINNER_SWEEP,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

// `width: 72px; height: 72px`, and the circle's own geometry inside a 50-unit
// viewBox: `stroke-width: 4` and `r: 20` of 25, so the ring is four fifths of
// the box across.
private val SPINNER_SIZE: Dp = 72.dp
private const val SPINNER_STROKE_SHARE = 4f / 50f
private const val SPINNER_RING_SHARE = 40f / 50f

// `stroke-dasharray: 100 28` against a circumference of 2 pi r — a hundred units
// of a hundred and twenty-six, as an angle.
private const val SPINNER_SWEEP = 286.5f

// `animation: nm-spin 0.9s linear infinite`.
private const val SPIN_MS = 900
private const val FULL_TURN = 360f

// `.center-btn { width: 80px; height: 80px }`, which is what the reference draws.
private val CENTER_BUTTON_SIZE = 80.dp

internal const val CENTER_PLAY_TAG = "nm-center-play"

// One question, named, because three clauses in an `if` is three things a
// reader has to hold at once and detekt counts them for the same reason.
private fun offersCentrePlay(scene: ChromeScene, offered: Boolean): Boolean =
    scene.formFactor == FormFactor.Desktop && offered && !scene.state.buffering
