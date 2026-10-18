// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ShareHoverGlyphTest {

    @Test
    fun theFilledShareGlyphIsAClosedShape() {
        val path: VectorPath = FluentIcons.ShareHover.root.first() as VectorPath
        val nodes: List<PathNode> = path.pathData

        assertIs<PathNode.Close>(nodes.last(), "the filled share glyph ends mid-curve: ${nodes.last()}")
    }

    @Test
    fun theFilledShareGlyphReturnsToItsStartingPoint() {
        val path: VectorPath = FluentIcons.ShareHover.root.first() as VectorPath
        val start: PathNode.MoveTo = assertIs(path.pathData.first())
        val beforeClose: PathNode = path.pathData[path.pathData.lastIndex - 1]
        val end: PathNode.CurveTo = assertIs(beforeClose)

        assertEquals(start.x, end.x3, 0.001f)
        assertEquals(start.y, end.y3, 0.001f)
    }
}
