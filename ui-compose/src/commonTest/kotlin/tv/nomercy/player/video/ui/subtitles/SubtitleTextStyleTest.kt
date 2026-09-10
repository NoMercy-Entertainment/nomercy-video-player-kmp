// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.ui.text.TextStyle
import tv.nomercy.player.core.events.SubtitleStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The web overlay is the oracle for caption appearance. These pin the three
// numbers a viewer actually sees — size against the picture, the halo the
// default edge style carries, and the window behind the cue — because the
// first port read them as fixed sp with a single soft shadow and the captions
// came out unreadable on a real screen.
class SubtitleTextStyleTest {

    @Test
    fun sizeScalesWithThePictureNotWithAFixedSp() {
        val style = SubtitleStyle()

        assertEquals(24f, style.fontSizeSp(960f))
        assertEquals(48f, style.fontSizeSp(1920f))
    }

    @Test
    fun sizeIsClampedTheWayTheWebClampsIt() {
        val style = SubtitleStyle()

        assertEquals(14f, style.fontSizeSp(320f))
        assertEquals(56f, style.fontSizeSp(4000f))
    }

    @Test
    fun theViewersPercentageStillMoves() {
        // At 1920 rather than 960: half of 24 is under the web's own 14px
        // floor, so the clamp would answer instead of the percentage.
        assertEquals(24f, SubtitleStyle(fontSize = 50).fontSizeSp(1920f))
        assertEquals(72f.coerceAtMost(56f), SubtitleStyle(fontSize = 150).fontSizeSp(1920f))
    }

    // `textShadow` is the default, and on the web it is seven stacked haloes.
    // A null outline pass here is a caption with nothing but a soft shadow.
    @Test
    fun theDefaultEdgeStyleCarriesAnOutlinePass() {
        val style = SubtitleStyle()
        val base: TextStyle = style.toTextStyle(960f)

        val outline: TextStyle? = style.toOutlineStyle(base, 6f)

        assertNotNull(outline)
        assertNotNull(base.shadow)
    }

    /**
     * `uniform` is the soft one and must not borrow the outline pass.
     *
     * Both used to claim it, and with the same zero-offset blur underneath that
     * made them draw identical pixels — two menu rows for one style. The web
     * tells them apart by how many times the shadow is stacked, one against
     * seven, so the stroke belongs to the dense one alone.
     */
    @Test
    fun uniformIsASoftHaloAndNotTheOutline() {
        val uniform = SubtitleStyle(edgeStyle = "uniform")
        val textShadow = SubtitleStyle(edgeStyle = "textShadow")

        val uniformBase: TextStyle = uniform.toTextStyle(960f)
        val textShadowBase: TextStyle = textShadow.toTextStyle(960f)

        assertNull(uniform.toOutlineStyle(uniformBase, 6f))
        assertNotNull(textShadow.toOutlineStyle(textShadowBase, 6f))
        // Both still carry the blur — the stroke is the only difference.
        assertNotNull(uniformBase.shadow)
        assertNotNull(textShadowBase.shadow)
    }

    @Test
    fun anOffsetEdgeStyleNeedsNoOutlinePass() {
        val style = SubtitleStyle(edgeStyle = "dropShadow")

        assertNull(style.toOutlineStyle(style.toTextStyle(960f), 6f))
    }

    @Test
    fun noEdgeStyleDrawsNoShadowAtAll() {
        assertNull(SubtitleStyle(edgeStyle = "none").toTextStyle(960f).shadow)
    }

    @Test
    fun theWindowIsTransparentUntilTheViewerAsksForIt() {
        assertEquals(0f, SubtitleStyle().toAreaColor().alpha)
        assertTrue(SubtitleStyle(windowOpacity = 100).toAreaColor().alpha > 0.99f)
    }
}
