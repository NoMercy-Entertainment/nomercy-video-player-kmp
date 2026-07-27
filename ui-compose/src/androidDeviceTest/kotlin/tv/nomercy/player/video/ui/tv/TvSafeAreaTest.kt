// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import tv.nomercy.player.core.device.DEFAULT_TV_OVERSCAN
import tv.nomercy.player.core.device.SafeAreaInsets
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Measured on a real device, because the question is where a thing ended up.
//
// The rule under it is that the inset comes from the contract and not from a
// number written into the widget. That is what the client this replaces got
// wrong: a fixed inset per widget, so a television that cropped more lost its
// controls and no single change could fix it.
class TvSafeAreaTest {

    @get:Rule
    val compose = createComposeRule()

    private fun offsetUnder(insets: SafeAreaInsets): Pair<Float, Float> {
        compose.setContent {
            CompositionLocalProvider(LocalSafeAreaInsets provides insets) {
                Box(Modifier.fillMaxSize().tvSafeArea()) {
                    Box(Modifier.fillMaxSize().testTag(INNER))
                }
            }
        }

        val bounds = compose.onNodeWithTag(INNER).fetchSemanticsNode().boundsInRoot
        return bounds.left to bounds.top
    }

    @Test
    fun theInsetIsWhateverTheContractSaidRatherThanAConstant() {
        val (left, top) = offsetUnder(SafeAreaInsets(left = 64f, top = 36f, right = 64f, bottom = 36f))

        with(compose.density) {
            assertEquals(64.dp.roundToPx().toFloat(), left)
            assertEquals(36.dp.roundToPx().toFloat(), top)
        }
    }

    @Test
    fun aDifferentContractMovesItADifferentAmount() {
        // The half that catches a widget with the number baked in: it would pass
        // the case above whenever the constant happened to match.
        val (left, _) = offsetUnder(DEFAULT_TV_OVERSCAN)

        with(compose.density) {
            assertEquals(DEFAULT_TV_OVERSCAN.left.dp.roundToPx().toFloat(), left)
        }
    }

    @Test
    fun withNothingToAvoidTheContentStartsAtTheEdge() {
        // Every surface that is not a television. A default inset here would put
        // a border around the picture on a phone and on a desktop.
        val (left, top) = offsetUnder(SafeAreaInsets())

        assertEquals(0f, left)
        assertEquals(0f, top)
    }

    @Test
    fun theInsetIsTakenFromAllFourEdgesRatherThanJustTheStart() {
        // A padding applied only to the leading edge reads as correct on the
        // left of the screen and loses whatever sits at the bottom.
        compose.setContent {
            CompositionLocalProvider(
                LocalSafeAreaInsets provides SafeAreaInsets(left = 40f, top = 20f, right = 40f, bottom = 20f),
            ) {
                Box(Modifier.fillMaxSize().tvSafeArea()) {
                    Box(Modifier.fillMaxSize().testTag(INNER))
                }
            }
        }

        val root = compose.onNodeWithTag(INNER).fetchSemanticsNode()
        val bounds = root.boundsInRoot
        val rootSize = root.root?.let { it.semanticsOwner.rootSemanticsNode.size }

        assertTrue(rootSize != null, "the composition reported no root to measure against")
        with(compose.density) {
            assertEquals(rootSize.width - 40.dp.roundToPx(), bounds.right.toInt())
            assertEquals(rootSize.height - 20.dp.roundToPx(), bounds.bottom.toInt())
        }
    }
}

private const val INNER = "safe-area-inner"
