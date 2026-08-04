// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val CONTROL_SIZE = 64.dp

// One button, driven by what the player says it is doing.
//
// [playing] comes from the engine rather than from a click, so a player that
// stops on its own — end of item, audio focus lost, a plugin refusing the
// transport — is drawn accurately without the button knowing why.
//
// The glyphs are drawn rather than imported. A play triangle and two bars are
// four lines of geometry, and pulling a Material icon artifact in to get them
// would put a font-sized dependency in every consumer's build for two shapes.
@Composable
public fun PlayPauseControl(
    playing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    val interaction: MutableInteractionSource = remember { MutableInteractionSource() }

    Canvas(
        modifier = modifier
            .size(CONTROL_SIZE)
            // No indication, and a circle if anything ever draws one.
            //
            // A bare `clickable` takes Compose's default: a rounded-RECTANGLE
            // ripple around an unclipped Canvas. That is the square ring around
            // the play triangle Stoney reported six times — the button was round
            // and the thing drawn over it was not, which is why every answer read
            // off the shape in the source came back "it is a circle" and was
            // useless. PlayerIconButton already passes `indication = null` and
            // paints its own states; this is the same rule.
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .semantics { contentDescription = if (playing) PAUSE_LABEL else PLAY_LABEL },
    ) {
        if (playing) {
            val barWidth: Float = size.width / BAR_WIDTH_DIVISOR
            val gap: Float = barWidth
            val left: Float = (size.width - (barWidth * 2 + gap)) / 2
            drawRect(tint, Offset(left, 0f), Size(barWidth, size.height))
            drawRect(tint, Offset(left + barWidth + gap, 0f), Size(barWidth, size.height))
        } else {
            drawPath(
                path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2)
                    lineTo(0f, size.height)
                    close()
                },
                color = tint,
            )
        }
    }
}

internal const val PLAY_LABEL: String = "Play"
internal const val PAUSE_LABEL: String = "Pause"
private const val BAR_WIDTH_DIVISOR = 4
