// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.thumbnails

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.thumbnails.spriteFrameAspect
import kotlin.math.roundToInt

// The thumbnail a viewer sees while dragging the scrubber.
//
// There is no offset arithmetic here and nothing correcting for a sheet that got
// shrunk on the way in. That correction existed only while the whole sheet was
// being decoded and downscaled to fit in memory, which scaled every cue
// coordinate with it; the tile source hands back the frame itself, at the
// resolution it was stored at.
//
// A null tile means the band behind this frame is still being read. Drawing
// nothing for a pass or two reads as the thumbnail arriving, because the surface
// behind it is already dark — whereas a placeholder would flash on every scrub.
@Composable
public fun SpriteFramePreview(
    sprite: PreviewSprite,
    seconds: Double,
    modifier: Modifier = Modifier,
    width: Dp = PREVIEW_WIDTH,
) {
    val tile: ImageBitmap? = sprite.frameAt(seconds)
    val aspect: Float = spriteFrameAspect(sprite.frames)

    Canvas(
        // The height comes from the declared aspect rather than from the tile,
        // so the box is the same size before and after the pixels land. Sizing
        // it from the image makes it grow under the viewer's thumb.
        modifier = modifier
            .width(width)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height: Int = (placeable.width / aspect).roundToInt()
                layout(placeable.width, height) { placeable.place(0, 0) }
            },
    ) {
        if (tile != null) {
            drawImage(
                image = tile,
                srcOffset = IntOffset(0, 0),
                srcSize = IntSize(tile.width, tile.height),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
        }
    }
}

// What the mobile chrome has always drawn them at. Named rather than left to
// each caller, because two chromes picking their own would give a viewer two
// different preview sizes in the same app.
public val PREVIEW_WIDTH: Dp = 200.dp
