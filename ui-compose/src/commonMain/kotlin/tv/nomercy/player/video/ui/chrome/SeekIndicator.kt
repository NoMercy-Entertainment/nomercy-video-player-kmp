// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The circle a double-tap puts on the picture.
//
// This was one line of 24sp white text. The web draws a 72px disc pinned to the
// edge, a double chevron above the figure, and fades and grows it into place —
// and none of that is decoration: a bare number floating over a frame reads as
// something having gone wrong, and it gives no clue which way the film just went.
//
// Every number is one declaration from `ensureStyles()` in the web's touch-zones
// plugin. They live THERE and not in desktop-ui/styles.css, because the plugin
// injects its own stylesheet — reading the chrome's would have found nothing and
// invented the lot.
//
// check-chrome-parity.py reads these constants and that stylesheet and compares
// them.
@Composable
public fun SeekIndicator(
    run: SeekRun,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    // 120ms on both, `ease-out` in the stylesheet. The scale is what makes it
    // read as arriving rather than appearing: at rest the disc is 0.85 and it
    // settles at 1.
    val fade: Float by animateFloatAsState(
        targetValue = if (visible) 1f else HIDDEN_OPACITY,
        animationSpec = tween(durationMillis = FADE_MS),
    )
    val grow: Float by animateFloatAsState(
        targetValue = if (visible) 1f else RESTING_SCALE,
        animationSpec = tween(durationMillis = FADE_MS),
    )

    Column(
        modifier = modifier
            .alpha(fade)
            .scale(grow)
            .size(DISC_SIZE)
            .clip(CircleShape)
            .background(DISC_COLOR)
            .testTag(if (run.side == SeekSide.Back) INDICATOR_BACK else INDICATOR_FORWARD),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CONTENT_GAP, Alignment.CenterVertically),
    ) {
        Canvas(modifier = Modifier.size(CHEVRON_SIZE)) {
            drawChevrons(run.side == SeekSide.Back)
        }

        BasicText(text = run.label, style = LABEL_STYLE)
    }
}

// The web's own path data, as points.
//
// `M11 17l-5-5 5-5M18 17l-5-5 5-5` and its mirror, on a 24-unit box: two
// three-point polylines each, stroked rather than filled. Kept as coordinates
// rather than redrawn by eye, because a chevron drawn freehand is a different
// glyph at the one size it is shown at.
private fun DrawScope.drawChevrons(pointingBack: Boolean) {
    val unit: Float = size.minDimension / VIEW_BOX
    val arms: List<List<Offset>> = if (pointingBack) BACK_CHEVRONS else FORWARD_CHEVRONS

    for (arm in arms) {
        val path = Path()
        arm.forEachIndexed { index, point ->
            val x: Float = point.x * unit
            val y: Float = point.y * unit
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = STROKE_COLOR,
            style = Stroke(
                width = STROKE_WIDTH * unit,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

// `width: 72px; height: 72px` and `border-radius: 50%`.
private val DISC_SIZE: Dp = 72.dp

// `gap: 6px` between the chevrons and the figure.
private val CONTENT_GAP: Dp = 6.dp

// `.nm-seek-indicator svg { width: 20px; height: 20px }`.
private val CHEVRON_SIZE: Dp = 20.dp

// `background: rgba(0,0,0,0.45)`.
private val DISC_COLOR: Color = Color.Black.copy(alpha = 0.45f)

// `stroke: #fff` and `stroke-width: 2.2`, in viewBox units — the stroke scales
// with the glyph, which is what an svg does and what a fixed dp width would not.
private val STROKE_COLOR: Color = Color.White
private const val STROKE_WIDTH = 2.2f

// `viewBox="0 0 24 24"`.
private const val VIEW_BOX = 24f

// `transform: translateY(-50%) scale(0.85)` at rest, `scale(1)` when shown, and
// `opacity: 0` to `1` — both over `120ms ease-out`.
private const val RESTING_SCALE = 0.85f
private const val HIDDEN_OPACITY = 0f
private const val FADE_MS = 120

// `color: #fff; font-size: 0.78rem; font-weight: 600`.
private const val REM = 16f
private val LABEL_STYLE = TextStyle(
    color = Color.White,
    fontSize = (0.78f * REM).sp,
    fontWeight = FontWeight(600),
)

private val BACK_CHEVRONS: List<List<Offset>> = listOf(
    listOf(Offset(11f, 17f), Offset(6f, 12f), Offset(11f, 7f)),
    listOf(Offset(18f, 17f), Offset(13f, 12f), Offset(18f, 7f)),
)

private val FORWARD_CHEVRONS: List<List<Offset>> = listOf(
    listOf(Offset(13f, 7f), Offset(18f, 12f), Offset(13f, 17f)),
    listOf(Offset(6f, 7f), Offset(11f, 12f), Offset(6f, 17f)),
)
