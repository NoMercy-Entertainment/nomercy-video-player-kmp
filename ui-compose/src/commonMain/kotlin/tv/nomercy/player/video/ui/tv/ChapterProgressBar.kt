// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// The progress bar, with the chapter breaks marked on it.
//
// The breaks are what make it worth drawing at all on a television: a viewer
// scrubbing with a remote is aiming at a chapter, and a plain bar gives them
// nothing to aim at.
@Composable
public fun ChapterProgressBar(
    timeSeconds: Double,
    durationSeconds: Double,
    chapters: List<TvChapter>,
    modifier: Modifier = Modifier,
) {
    val progress: Float = progressOf(timeSeconds, durationSeconds)

    Canvas(
        modifier = modifier
            .height(BAR_HEIGHT)
            .testTag(PROGRESS_TAG)
            // Reported rather than only drawn, so a test can ask where the bar
            // is and a screen reader can say it.
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            },
    ) {
        drawRect(color = TRACK, size = size)
        drawRect(color = FILL, size = Size(size.width * progress, size.height))

        // Drawn last so a marker sitting under the playhead is still visible;
        // a break the fill has covered is a break a viewer cannot aim at.
        for (chapter in chapters) {
            val at: Float = progressOf(chapter.startSeconds, durationSeconds)
            if (at <= 0f || at >= 1f) continue

            drawRect(
                color = MARKER,
                topLeft = Offset(size.width * at, 0f),
                size = Size(MARKER_WIDTH_PX, size.height),
            )
        }
    }
}

// Zero rather than a division by zero, which is what a live stream is: the
// duration is unknown and a bar has nothing to fill against.
private fun progressOf(seconds: Double, duration: Double): Float =
    if (duration <= 0.0) 0f else (seconds / duration).coerceIn(0.0, 1.0).toFloat()

internal const val PROGRESS_TAG = "tv-progress-bar"

private val BAR_HEIGHT = 6.dp
private const val MARKER_WIDTH_PX = 2f

private val TRACK = Color(0x40FFFFFF)
private val FILL = Color(0xFFFFFFFF)
private val MARKER = Color(0xFF000000)
