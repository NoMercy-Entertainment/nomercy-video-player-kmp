// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlin.test.Test
import kotlin.test.assertEquals

// The numbers behind the words the settings menu writes.
//
// Every value here is one a viewer already chose in a browser. `edgeStyle` and
// the eight colour names were tokens with nothing behind them, so a preference
// carried across from the web named a style this player could not draw — and
// the failure is silent: the subtitle appears with no outline, which reads as
// the font being wrong rather than as a setting that did not arrive.
class SubtitlePaintTest {

    @Test
    fun aNamedColourResolvesToTheValueABrowserDraws() {
        assertEquals("#00FFFFFF", parseColorToHex("cyan", 1.0))
        assertEquals("#FFFF00FF", parseColorToHex("yellow", 1.0))
        assertEquals("#808080FF", parseColorToHex("gray", 1.0))
    }

    @Test
    fun theMenusEightColoursAllHaveOne() {
        // The pane offers these by name and writes the name into the style. One
        // without a value here is a row that draws nothing when it is picked.
        for (name in listOf("black", "blue", "cyan", "green", "magenta", "red", "yellow", "white")) {
            assertEquals(
                true,
                NAMED_SUBTITLE_COLORS.containsKey(name),
                "the menu offers $name and nothing resolves it",
            )
        }
    }

    @Test
    fun theOpacityIsFoldedIntoTheAlphaByte() {
        // Not carried alongside. A background at 0% has to arrive at the
        // renderer already gone, or something downstream draws it opaque.
        assertEquals("#000000FF", parseColorToHex("black", 1.0))
        assertEquals("#00000000", parseColorToHex("black", 0.0))
        assertEquals("#000000BF", parseColorToHex("black", 0.75))
    }

    @Test
    fun transparentShortCircuits() {
        assertEquals("#00000000", parseColorToHex("transparent", 1.0))
    }

    @Test
    fun aShortHexIsWidenedBeforeItsAlphaIsAdded() {
        assertEquals("#00FF00FF", parseColorToHex("#0F0", 1.0))
    }

    @Test
    fun aColourThatCarriesItsOwnAlphaKeepsIt() {
        // The viewer set that alpha deliberately. Folding the style's opacity on
        // top would multiply two settings they chose once.
        assertEquals("#11223344", parseColorToHex("#11223344", 0.5))
    }

    @Test
    fun anRgbStringBecomesTheSameBytes() {
        assertEquals("#00FF00FF", parseColorToHex("rgb(0, 255, 0)", 1.0))
        assertEquals("#0A141E80", parseColorToHex("rgba(10, 20, 30, 0.5)", 0.5))
    }

    @Test
    fun aColourNothingCanResolveIsTransparentRatherThanGuessed() {
        // The web asks a canvas at this point and there is none here. A wrong
        // colour is worse than none: it is a subtitle the viewer cannot read and
        // cannot explain.
        assertEquals("#00000000", parseColorToHex("hsl(120 100% 50%)", 1.0))
    }

    @Test
    fun everyEdgeStyleKeepsItsOwnOffsetsAndBlur() {
        // `raised` and `depressed` are the same shadow with the sign flipped, so
        // a lost minus is not a near miss — it is the other style under this
        // one's name, and the setting the viewer picked becomes unreachable.
        assertEquals(SubtitleEdge(1.0, 1.0, 2.0, 1), subtitleEdgeOf("depressed"))
        assertEquals(SubtitleEdge(2.0, 2.0, 4.0, 1), subtitleEdgeOf("dropShadow"))
        assertEquals(SubtitleEdge(-1.0, -1.0, 2.0, 1), subtitleEdgeOf("raised"))
        assertEquals(SubtitleEdge(0.0, 0.0, 4.0, 1), subtitleEdgeOf("uniform"))
    }

    @Test
    fun theDefaultStyleStacksItsShadowSevenTimes() {
        // `Array.from({ length: 7 })` in the web's own table. One copy is a faint
        // halo; seven is an outline that survives a snow scene, which is why
        // `textShadow` is the default there and here.
        assertEquals(7, subtitleEdgeOf("textShadow").layers)
    }

    @Test
    fun noneDrawsNothingAndSoDoesAWordNobodyKnows() {
        assertEquals(NO_EDGE, subtitleEdgeOf("none"))
        assertEquals(NO_EDGE, subtitleEdgeOf("outline"))
        assertEquals(0, NO_EDGE.layers)
    }
}
