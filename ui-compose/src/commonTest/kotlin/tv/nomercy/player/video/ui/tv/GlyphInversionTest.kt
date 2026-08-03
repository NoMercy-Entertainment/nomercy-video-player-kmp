// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

// The half of the bar's visual language that was generated and never drawn.
//
// `.btn:hover .icon-normal { display: none }` and `.btn:hover .icon-hover
// { display: inline }`, with `.btn.is-active` on the same rule: every bottom-bar
// control is outlined at rest and filled once a pointer is on it or the control
// is on. Thirty-seven inverted variants were generated from the same table the
// normal ones come from, and not one of them was referenced anywhere — so a
// pointer crossing the bar got a 1.1 scale and nothing else, and a muted volume
// looked exactly like an unmuted one.
class GlyphInversionTest {

    @Test
    fun aPointerOnAControlInvertsItsDrawing() {
        val inverted: ImageVector = glyphFor(FluentIcons.Fullscreen, inverted = true)

        assertSame(FluentIcons.FullscreenHover, inverted)
    }

    @Test
    fun andAtRestItIsTheOutlinedOne() {
        assertSame(FluentIcons.Fullscreen, glyphFor(FluentIcons.Fullscreen, inverted = false))
    }

    @Test
    fun anIconWithNoSecondDrawingKeepsTheOneItHas() {
        // A consumer's own glyph, and the handful of the web's own that have a
        // single drawing. A control with one drawing does not invert; it must not
        // vanish.
        val mine: ImageVector = ImageVector.Builder(
            name = "not-in-the-table",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).build()

        assertSame(mine, glyphFor(mine, inverted = true))
    }

    @Test
    fun theTableAnswersForEveryControlTheBarDraws() {
        // Named one by one rather than counted, because the failure this catches
        // is a control whose pair stopped resolving — which a count cannot see.
        listOf(
            FluentIcons.Play to FluentIcons.PlayHover,
            FluentIcons.Pause to FluentIcons.PauseHover,
            FluentIcons.Next to FluentIcons.NextHover,
            FluentIcons.Previous to FluentIcons.PreviousHover,
            FluentIcons.Fullscreen to FluentIcons.FullscreenHover,
            FluentIcons.Settings to FluentIcons.SettingsHover,
        ).forEach { (normal, hover) ->
            assertEquals(hover, assertNotNull(FluentIcons.hoverFor(normal), "no pair for ${normal.name}"))
        }
    }
}
