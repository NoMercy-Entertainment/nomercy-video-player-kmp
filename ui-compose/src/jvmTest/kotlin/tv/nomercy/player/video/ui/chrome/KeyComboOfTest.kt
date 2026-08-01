// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import tv.nomercy.player.core.input.KeyCombo
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.core.input.asCombo
import tv.nomercy.player.core.input.keyCombo
import kotlin.test.Test
import kotlin.test.assertEquals

// A press, as the binding table spells it.
//
// The table is a map keyed by a string, so a mapping that produces "M" where the
// binding says "m" is not an error anywhere: the lookup misses, the press does
// nothing, and there is no way to tell that from a key nobody bound. Which is
// why this asserts the exact strings rather than that something was handled.
@OptIn(ExperimentalTestApi::class)
class KeyComboOfTest {

    private fun ComposeUiTest.comboFrom(press: KeyInjection): KeyCombo? {
        var seen: KeyCombo? = null

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TAG)
                    .onKeyEvent { event ->
                        keyComboOf(event)?.also { seen = it } != null
                    }
                    .focusable(),
            )
        }
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { press() }
        waitForIdle()

        return seen
    }

    @Test
    fun spaceIsTheOneTheTransportIsBoundTo() = runComposeUiTest {
        assertEquals(keyCombo(" "), comboFrom { pressKey(Key.Spacebar) })
    }

    @Test
    fun aLetterArrivesLowerCase() = runComposeUiTest {
        assertEquals(keyCombo("m"), comboFrom { pressKey(Key.M) })
    }

    @Test
    fun andStaysLowerCaseUnderShift() = runComposeUiTest {
        // The modifier is already in the combo. A shifted letter spelled upper
        // case would be a second entry in the map, and the one the table holds
        // would never fire.
        assertEquals(
            keyCombo("n", shift = true),
            comboFrom { withKeyDown(Key.ShiftLeft) { pressKey(Key.N) } },
        )
    }

    @Test
    fun aFunctionKeyIsNamed() = runComposeUiTest {
        assertEquals(keyCombo("F11"), comboFrom { pressKey(Key.F11) })
    }

    @Test
    fun escapeArrivesAsTheRemotesBackButton() = runComposeUiTest {
        // Not "Escape". `playerKeyOf` is asked first and it folds a keyboard's
        // Escape into the same [PlayerKey] a remote's back button sends, so this
        // is the string every binding and every predicate has to be written
        // against. Both of the ones that cared had been written against the
        // web's spelling instead, and neither could ever fire.
        assertEquals(PlayerKey.Back.asCombo(), comboFrom { pressKey(Key.Escape) })
    }

    @Test
    fun andAnArrowKeepsTheNameTheRemoteUses() = runComposeUiTest {
        // The same string a television sends, because it is the same press and
        // two names for it would be two bindings that have to be kept in step.
        assertEquals(keyCombo("ArrowLeft"), comboFrom { pressKey(Key.DirectionLeft) })
    }
}

private const val TAG = "key-probe"

// The press to make, written where the test reads rather than as three
// arguments describing one.
private typealias KeyInjection = androidx.compose.ui.test.KeyInjectionScope.() -> Unit
