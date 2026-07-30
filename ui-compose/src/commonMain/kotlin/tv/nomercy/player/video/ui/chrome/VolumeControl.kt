// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import tv.nomercy.player.video.ui.tv.PlayerIconButton
import tv.nomercy.player.video.ui.tv.TvChromeStrings

/**
 * The mute button, and a way to set the level.
 *
 * There was no way to set it. The bar drew a mute button and the responsive
 * arithmetic reserved 96dp beside it for a slider — `CHROME_VOLUME_SLIDER_WIDTH`,
 * which is the web's 80px track plus its two 8px margins — and nothing was ever
 * drawn in that space. A viewer could mute and unmute and could not turn it down.
 *
 * setVolume was on ChromeCommands the whole time with an implementation behind it.
 * No control called it.
 */
@Composable
public fun VolumeControl(
    state: ChromeState,
    commands: ChromeCommands,
    spec: VolumeSpec,
    modifier: Modifier = Modifier,
) {
    if (spec.vertical) {
        VerticalVolume(state, commands, spec, modifier)
    } else {
        HorizontalVolume(state, commands, spec, modifier)
    }
}

// `.volume-container:focus-within .volume-slider { width: 80px; margin: 0 8px }`
// — the track is 0 wide and transparent until the container has hover or focus,
// then grows. Compose has no focus-within, so the hover and focus of the row are
// collected and either one opens it, which is what that selector means.
@Composable
private fun HorizontalVolume(
    state: ChromeState,
    commands: ChromeCommands,
    spec: VolumeSpec,
    modifier: Modifier,
) {
    val interactions: MutableInteractionSource = remember { MutableInteractionSource() }
    val hovered: Boolean by interactions.collectIsHoveredAsState()
    val focused: Boolean by interactions.collectIsFocusedAsState()

    val open: Boolean = hovered || focused
    val width: Dp by animateDpAsState(
        targetValue = if (open) expandedWidthFor(spec.playerWidthDp) else 0.dp,
        animationSpec = tween(durationMillis = EXPAND_MS),
        label = "volume",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.hoverable(interactions).testTag(VOLUME_CONTROL_TAG),
    ) {
        MuteButton(state, commands, spec.label)

        if (width > 0.dp) {
            VolumeTrack(
                percent = displayedPercent(state),
                onSet = commands::setVolume,
                label = spec.label,
                modifier = Modifier
                    .padding(horizontal = TRACK_MARGIN)
                    .width(width)
                    .height(TRACK_THICKNESS),
            )
        }
    }
}

// `.volume-slider-vertical`: a card above the button holding its own mute button
// and a 4px by 80px track. Opened by pressing the volume button, and pressing it
// again closes it — the web's openVertPop toggles rather than only opening.
@Composable
private fun VerticalVolume(
    state: ChromeState,
    commands: ChromeCommands,
    spec: VolumeSpec,
    modifier: Modifier,
) {
    var open: Boolean by remember { mutableStateOf(false) }

    Box(modifier = modifier.testTag(VOLUME_CONTROL_TAG), contentAlignment = Alignment.BottomCenter) {
        MuteButton(state, commands, spec.label, onClick = { open = !open })

        if (open) {
            // A Popup for the same reason the tooltip is one: the card is
            // `position: absolute` above a 40dp button, and measured inside that
            // button it would be squeezed to 40dp wide.
            Popup(
                popupPositionProvider = AboveAnchorPosition(POPUP_OFFSET),
                onDismissRequest = { open = false },
            ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(POPUP_GAP),
                modifier = Modifier
                    .background(POPUP_BACKGROUND, RoundedCornerShape(POPUP_RADIUS))
                    .padding(horizontal = POPUP_PADDING_HORIZONTAL, vertical = POPUP_PADDING_VERTICAL)
                    .testTag(VOLUME_POPUP_TAG),
            ) {
                // The popup carries its own mute button — `.vol-popup-mute` — so a
                // viewer who opened the card can silence it without closing it
                // again to reach the one underneath.
                MuteButton(state, commands, spec.label, buttonSize = POPUP_MUTE_SIZE)

                VerticalVolumeTrack(
                    percent = displayedPercent(state),
                    onSet = commands::setVolume,
                    label = spec.label,
                )
            }
            }
        }
    }
}

@Composable
private fun MuteButton(
    state: ChromeState,
    commands: ChromeCommands,
    label: String,
    onClick: (() -> Unit)? = null,
    buttonSize: Dp = CONTROL_SIZE,
) {
    PlayerIconButton(
        icon = volumeIconFor(state),
        description = label,
        onClick = onClick ?: { commands.setMuted(!state.muted) },
        buttonSize = buttonSize,
    )
}

/**
 * The horizontal track: filled to [percent], white, with a round thumb on it.
 *
 * `linear-gradient(to right, #fff 0%, #fff var(--vol-pct), rgba(255,255,255,0.3)
 * var(--vol-pct))` — two flat colours meeting at the level, not a blend.
 */
@Composable
private fun VolumeTrack(
    percent: Int,
    onSet: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .semantics { contentDescription = "$label $percent%" }
            .testTag(VOLUME_TRACK_TAG)
            .pointerInput(Unit) { detectTapGestures { at -> onSet(percentAt(at.x / size.width)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> onSet(percentAt(change.position.x / size.width)) }
            },
    ) {
        val filled: Float = size.width * (percent / PERCENT_MAX)
        val radius = CornerRadius(size.height / 2f, size.height / 2f)

        drawRoundRect(color = TRACK_REST, cornerRadius = radius)
        if (filled > 0f) {
            drawRoundRect(color = Color.White, size = Size(filled, size.height), cornerRadius = radius)
        }

        drawCircle(
            color = Color.White,
            radius = THUMB_SIZE.toPx() / 2f,
            center = Offset(filled.coerceIn(0f, size.width), size.height / 2f),
        )
    }
}

// `writing-mode: vertical-lr; direction: rtl` with a 4px width and 80px height:
// full at the top, empty at the bottom, so dragging up is louder.
@Composable
private fun VerticalVolumeTrack(percent: Int, onSet: (Int) -> Unit, label: String) {
    Canvas(
        modifier = Modifier
            .width(VERTICAL_TRACK_THICKNESS)
            .height(VERTICAL_TRACK_LENGTH)
            .semantics { contentDescription = "$label $percent%" }
            .testTag(VOLUME_TRACK_TAG)
            .pointerInput(Unit) {
                detectTapGestures { at -> onSet(percentAt(1f - at.y / size.height)) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    onSet(percentAt(1f - change.position.y / size.height))
                }
            },
    ) {
        val filled: Float = size.height * (percent / PERCENT_MAX)
        val radius = CornerRadius(size.width / 2f, size.width / 2f)

        drawRoundRect(color = TRACK_REST, cornerRadius = radius)
        if (filled > 0f) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(0f, size.height - filled),
                size = Size(size.width, filled),
                cornerRadius = radius,
            )
        }

        drawCircle(
            color = Color.White,
            radius = THUMB_SIZE.toPx() / 2f,
            center = Offset(size.width / 2f, (size.height - filled).coerceIn(0f, size.height)),
        )
    }
}

// Muted reads as nought rather than as whatever it was before, because that is
// what the bar's glyph already says and two answers to "how loud is it" is one
// too many.
private fun displayedPercent(state: ChromeState): Int = if (state.muted) 0 else state.volume

private fun percentAt(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * PERCENT_MAX).toInt()

private const val PERCENT_MAX = 100f

private val CONTROL_SIZE: Dp = 40.dp

// `height: 8px`, `margin: 0 8px`, a 12px thumb.
private val TRACK_THICKNESS: Dp = 8.dp
private val TRACK_MARGIN: Dp = 8.dp
private val THUMB_SIZE: Dp = 12.dp
private val TRACK_REST: Color = Color.White.copy(alpha = 0.3f)

// `transition: width 0.3s ease`.
private const val EXPAND_MS = 300

// `.volume-slider-vertical`: bottom calc(100% + 8px), radius 8, padding 12px 10px,
// gap 8, rgba(20, 22, 30, 0.92). `.vol-popup-mute` has a 44px minimum.
private val POPUP_OFFSET: Dp = 8.dp
private val POPUP_RADIUS: Dp = 8.dp
private val POPUP_PADDING_VERTICAL: Dp = 12.dp
private val POPUP_PADDING_HORIZONTAL: Dp = 10.dp
private val POPUP_GAP: Dp = 8.dp
private val POPUP_MUTE_SIZE: Dp = 44.dp
private val POPUP_BACKGROUND: Color = Color(red = 20, green = 22, blue = 30, alpha = 235)

// `width: 4px; height: 80px`.
private val VERTICAL_TRACK_THICKNESS: Dp = 4.dp
private val VERTICAL_TRACK_LENGTH: Dp = 80.dp

internal const val VOLUME_CONTROL_TAG = "nm-volume-control"
internal const val VOLUME_TRACK_TAG = "nm-volume-track"
internal const val VOLUME_POPUP_TAG = "nm-volume-popup"


// The label and the layout inputs as one value, so the bar hands the control a
// decision rather than five arguments.
internal fun volumeSpecFor(
    state: ChromeState,
    strings: TvChromeStrings,
    mode: VolumeSliderMode,
    playerWidthDp: Int,
): VolumeSpec = VolumeSpec(
    label = if (state.muted) strings.unmute else strings.mute,
    mode = mode,
    playerWidthDp = playerWidthDp,
)
