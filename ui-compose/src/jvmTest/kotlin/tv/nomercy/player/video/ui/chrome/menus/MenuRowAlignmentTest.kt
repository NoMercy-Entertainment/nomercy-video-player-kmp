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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import tv.nomercy.player.video.ui.tv.FluentIcons
import kotlin.test.Test
import kotlin.test.assertTrue

private const val WIDTH = 320
private const val HEIGHT = 48
private const val TOLERANCE_PX = 2

/**
 * Every row starts its glyph in the same column, measured rather than assumed.
 *
 * Stoney reported the menu rows as misaligned and the answer came back twice
 * from reading path data — which cannot see what was drawn. The Fluent paths ARE
 * the web's, byte for byte, so if the rows still disagree the difference is in
 * the box around them and this is where it shows up.
 *
 * The first painted column of each row is the number. Equal across icons of very
 * different mass — a wide HD plate, a round gear, a thin chevron — means the slot
 * is doing its job; unequal means the glyph is positioning the row instead of the
 * row positioning the glyph.
 */
@OptIn(ExperimentalComposeUiApi::class)
class MenuRowAlignmentTest {

    @Test
    fun everyRowPutsItsGlyphInTheSameColumn() {
        val columns: Map<String, Int> = mapOf(
            "quality" to FluentIcons.Quality,
            "speed" to FluentIcons.Speed,
            "playlist" to FluentIcons.Playlist,
            "settings" to FluentIcons.Settings,
        ).mapValues { (_, icon) -> firstPaintedColumn(render(icon)) }

        // Within a pixel, not identical. Measured on 2026-08-04 the four came out
        // at 9, 9, 9 and 10: the slot is the same for every row and the last
        // pixel is the gear's own ink inside it, which is true of the same four
        // SVGs in a browser. A tolerance of two guards the slot without pinning
        // the artwork.
        val lowest: Int = columns.values.min()
        val highest: Int = columns.values.max()

        assertTrue(
            highest - lowest <= TOLERANCE_PX,
            "the glyph column drifts by ${highest - lowest}px across rows: $columns",
        )
    }

    // The leftmost column holding anything other than the backdrop.
    private fun firstPaintedColumn(frame: ImageBitmap): Int {
        val pixels = frame.toPixelMap()

        return (0 until frame.width).firstOrNull { x ->
            (0 until frame.height).any { y -> pixels[x, y] != Color.Black }
        } ?: -1
    }

    private fun render(icon: ImageVector): ImageBitmap {
        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                MenuRow(label = "Row", icon = icon, onSelect = {})
            }
        }
        try {
            return scene.render().toComposeImageBitmap()
        } finally {
            scene.close()
        }
    }
}
