// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// The transport glyphs, drawn rather than loaded.
//
// A resource identifier is an Android concept, and these have to exist on a
// desktop and on an Apple platform too. Drawn here they ship with the library,
// need no asset pipeline in a consumer, and cannot go missing from one target's
// build.
public object PlayerIcons {

    public val Play: ImageVector by lazy {
        glyph("Play") {
            moveTo(8f, 5f)
            lineTo(19f, 12f)
            lineTo(8f, 19f)
            close()
        }
    }

    public val Pause: ImageVector by lazy {
        glyph("Pause") {
            moveTo(6f, 5f)
            horizontalLineTo(10f)
            verticalLineTo(19f)
            horizontalLineTo(6f)
            close()
            moveTo(14f, 5f)
            horizontalLineTo(18f)
            verticalLineTo(19f)
            horizontalLineTo(14f)
            close()
        }
    }

    public val Next: ImageVector by lazy {
        glyph("Next") {
            moveTo(6f, 5f)
            lineTo(15f, 12f)
            lineTo(6f, 19f)
            close()
            moveTo(16f, 5f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(16f)
            close()
        }
    }

    public val Restart: ImageVector by lazy {
        glyph("Restart") {
            moveTo(18f, 5f)
            lineTo(9f, 12f)
            lineTo(18f, 19f)
            close()
            moveTo(5f, 5f)
            horizontalLineTo(8f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
        }
    }

    public val Subtitles: ImageVector by lazy {
        glyph("Subtitles") {
            moveTo(3f, 6f)
            horizontalLineTo(21f)
            verticalLineTo(18f)
            horizontalLineTo(3f)
            close()
            moveTo(5f, 13f)
            horizontalLineTo(11f)
            verticalLineTo(15f)
            horizontalLineTo(5f)
            close()
            moveTo(13f, 13f)
            horizontalLineTo(19f)
            verticalLineTo(15f)
            horizontalLineTo(13f)
            close()
        }
    }

    public val Episodes: ImageVector by lazy {
        glyph("Episodes") {
            moveTo(4f, 5f)
            horizontalLineTo(20f)
            verticalLineTo(8f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 10f)
            horizontalLineTo(20f)
            verticalLineTo(13f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 15f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            horizontalLineTo(4f)
            close()
        }
    }
}

// Twenty-four units square, which is what every icon set in this ecosystem
// draws on, so a consumer swapping one of these for its own does not have to
// rescale it.
private fun glyph(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = GLYPH_SIZE,
        defaultHeight = GLYPH_SIZE,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    )
        .path(fill = SolidColor(Color.White)) { draw() }
        .build()

private val GLYPH_SIZE = 24.dp
private const val VIEWPORT = 24f
