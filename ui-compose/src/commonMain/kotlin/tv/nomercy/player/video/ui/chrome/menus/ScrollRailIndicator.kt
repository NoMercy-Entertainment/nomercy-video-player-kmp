// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The thumb a scrolling list draws down its trailing edge.
 *
 * `.scroll-container { overflow-y: auto }` gets one from the browser, and the
 * rail's own `padding: 8px 0 8px 8px` leaves the trailing edge clear precisely
 * so it has somewhere to sit. Compose draws no scrollbar in common code at all,
 * so the padding read as a missing margin and a list that ran past the bottom of
 * the card gave no sign there was more of it.
 *
 * Proportional to what is on screen: the thumb's height is the visible fraction
 * of the list and its offset is how far down that fraction has travelled. Absent
 * entirely when everything fits, which is what a browser does too.
 */
@Composable
internal fun BoxScope.ScrollRailIndicator(state: LazyListState, modifier: Modifier = Modifier) {
    val info = state.layoutInfo
    val total: Int = info.totalItemsCount
    if (total == 0) return

    val onScreen: Int = info.visibleItemsInfo.size
    if (onScreen == 0 || onScreen >= total) return

    val visible: Float = onScreen.toFloat() / total
    val travelled: Float = state.firstVisibleItemIndex.toFloat() / (total - onScreen).coerceAtLeast(1)

    Box(
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(vertical = TRACK_INSET)
            .width(THUMB_WIDTH)
            .fillMaxHeight()
            .thumb(visible, travelled)
            .clip(RoundedCornerShape(THUMB_WIDTH / 2))
            .background(THUMB_COLOUR),
    )
}

// The thumb's own height and where it sits, both derived at layout time so the
// list's height does not have to be known in the composition.
private fun Modifier.thumb(visible: Float, travelled: Float): Modifier = layout { measurable, constraints ->
    val height: Int = (constraints.maxHeight * visible).toInt().coerceAtLeast(MIN_THUMB_PX)
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    val top: Int = ((constraints.maxHeight - height) * travelled).toInt()

    layout(placeable.width, constraints.maxHeight) { placeable.placeRelative(0, top) }
}

private val THUMB_WIDTH: Dp = 4.dp
private val TRACK_INSET: Dp = 8.dp
private val THUMB_COLOUR: Color = Color.White.copy(alpha = 0.35f)
private const val MIN_THUMB_PX = 24
