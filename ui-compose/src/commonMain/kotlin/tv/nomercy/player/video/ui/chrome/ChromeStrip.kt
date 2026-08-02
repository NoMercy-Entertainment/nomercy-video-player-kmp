// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

// The half of the bottom bar above the controls: `.top-row`, the strip inside
// it, and the two things that only exist while a position is being hunted.
//
// Split out of VideoChrome because the assembly there is the whole chrome and
// this is one row of it.
// `.bottom-bar-shadow`, which the top bar had and this did not. The controls
// were legible over a dark scene and gone over a bright one, and nothing in the
// campaign could see that: every check read a size.
//
// Drawn on the stack rather than as a sibling behind it because the web's is
// `height: calc(100% + 24px)` on a stack exactly as tall as its contents. A
// fixed-height box here would stop matching the moment a consumer hid the strip.
internal fun Modifier.bottomScrim(): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = BOTTOM_SCRIM,
            startY = -SCRIM_OVERHANG.toPx(),
            endY = size.height,
        ),
        topLeft = Offset(0f, -SCRIM_OVERHANG.toPx()),
        size = Size(size.width, size.height + SCRIM_OVERHANG.toPx()),
    )
}


// `.top-row`: the progress strip, the preview bubble and the drag handle.
//
// `onScrub` reports the position under the pointer — null when nothing is being
// hunted, which is the signal the bubble and the handle key on. It rides back to
// the caller because the bar's other half has no use for it.
@Composable
internal fun ChromeStrip(
    scene: ChromeScene,
    host: ChromeHost,
    rowWidth: Dp,
    onScrub: (Double?) -> Unit,
) {
    // Both derived from the row's width rather than passed alongside it: the
    // inset and the strip's own width are functions of the same container query,
    // and two parameters that must agree are two that can disagree.
    val metrics: BarMetrics = barMetricsFor(boundedWidthDp(rowWidth))
    val barWidth: Dp = rowWidth - metrics.stripPaddingHorizontal * 2

    var scrub: Double? by remember { mutableStateOf(null) }

        // `.top-row { padding: 0 24px; margin-top: 16px }` — the strip is
        // inset further than the controls and floats above them. It ran the
        // full width hard against the play button, which is the one part of
        // the bar a viewer aims at without looking.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = STRIP_TOP_MARGIN)
                .padding(horizontal = metrics.stripPaddingHorizontal)
                // `.top-row { height: 8px }`. The row is the height of the
                // BAR, not of what can be pressed.
                .height(STRIP_ROW_HEIGHT),
        ) {
            host.slots.scrubber?.invoke(scene.state, scene.commands) ?: ChapterScrubber(
                state = scene.state,
                commands = scene.commands,
                sprite = host.sprite,
                onScrubbing = scene.controller::setScrubbing,
                onScrub = {
                    scrub = it
                    onScrub(it)
                },
                // Overflows the row rather than setting it.
                //
                // The strip is 8dp of picture inside a 32dp target — a
                // three-pixel drag target is one nobody hits with a finger.
                // Taking that 32dp as LAYOUT height put 12dp of nothing
                // above the bar and 12 below, which pushed the strip most of
                // an icon's height further from the controls than the
                // browser puts it. The web has the same shape and the same
                // answer: `.slider-bar` grows to 12px inside a row that
                // stays 8 and simply overflows it.
                modifier = Modifier.requiredHeight(SCRUBBER_TOUCH_HEIGHT),
            )

            // `position: absolute; bottom: 24px` — an OVERLAY on the strip
            // rather than a row above it, and only while something is being
            // hunted. Stacked in the column it took a slot and one of the
            // column's 8dp gaps even while invisible, which pushed the strip
            // further from the controls than the browser puts it. An
            // absolutely positioned element takes no space in the flow, and
            // this one now takes none either.
            ScrubBubble(scene, host, scrub, barWidth)

            // Only while something is being hunted, which is the half of the
            // rule this read past:
            //
            //     .slider-nipple { display: none }
            //     .slider-bar:hover .slider-nipple { display: block }
            //
            // The base declaration states a 16px handle that is never drawn,
            // so a port reading the sizes takes it for the resting state and
            // draws a dot on every frame. The web's strip is a bare bar until
            // a pointer reaches it, and a permanent handle also pushed the
            // whole strip up: the 24dp dot is taller than the 8dp bar, so it
            // set the row's height and opened a gap above the controls that
            // the browser does not have.
            //
            // `scrub` is non-null exactly when `:hover` or `.slider-scrubbing`
            // is true, which is the union both rules key on — so it decides
            // whether the handle is there at all, and it is grown whenever it
            // is, as the stylesheet has it.
            val hunting: Double? = scrub
            if (hunting != null) {
                ScrubNipple(
                    fraction = scrubFraction(hunting, scene.state.durationSeconds),
                    barWidth = barWidth,
                    modifier = Modifier.align(Alignment.CenterStart),
                    grown = true,
                )
            }
        }
}
