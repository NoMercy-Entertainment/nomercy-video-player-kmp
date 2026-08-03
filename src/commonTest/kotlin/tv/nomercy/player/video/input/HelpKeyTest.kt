// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import tv.nomercy.player.core.device.DeviceCapabilities
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.input.KeyCombo
import tv.nomercy.player.core.input.keyCombo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The shortcut sheet: the discoverability half of every other binding, and the
// one key the reference binds that this handler did not.
class HelpKeyTest {

    private class Desktop(
        override val formFactor: FormFactor = FormFactor.Desktop,
        override val hasDpad: Boolean = false,
        override val hasTouch: Boolean = false,
        override val hasPointer: Boolean = true,
        override val hasHardwareVolumeKeys: Boolean = true,
        override val hasHdrDisplay: Boolean = false,
    ) : DeviceCapabilities

    private fun handler(): VideoKeyHandlerPlugin {
        val plugin = VideoKeyHandlerPlugin(
            commands = RecordingPlayerCommands().commands,
            capabilities = Desktop(),
            nowMs = { 0L },
        )
        plugin.use()
        return plugin
    }

    @Test
    fun theSheetStartsDownAndTheKeyTogglesIt() {
        val plugin = handler()

        assertFalse(plugin.shortcutsVisible.value, "the sheet was up before anybody asked")

        assertTrue(plugin.handle(keyCombo("?", shift = true)), "the help key was not bound")
        assertTrue(plugin.shortcutsVisible.value)

        plugin.handle(keyCombo("?", shift = true))
        assertFalse(plugin.shortcutsVisible.value, "the sheet only opened, it never closed")
    }

    @Test
    fun aBareQuestionMarkIsNotTheBindingBecauseThatIsNotWhatArrives() {
        // On a standard keyboard the question mark comes with shift held, and
        // the canonicaliser folds modifier state into the key. A binding on a
        // bare ? is a miss every time.
        val plugin = handler()

        assertFalse(plugin.handle(KeyCombo("?")))
        assertFalse(plugin.shortcutsVisible.value)
    }

    @Test
    fun theSheetIsReadOffTheLiveTableRatherThanARestatedList() {
        // A sheet built from a hand-written list drifts from the bindings, which
        // has already happened once here: the panel promised five seconds and
        // the player moved ten.
        val plugin = handler()

        val listed: List<String> = plugin.shortcuts().map { it.canonical }

        assertTrue(listed.isNotEmpty(), "the sheet had nothing to print")
        assertTrue("shift+?" in listed, "the help key is missing from its own sheet")
        assertTrue(" " in listed, "play/pause is missing from the sheet")
        assertEquals(listed.size, listed.distinct().size, "the sheet lists a combo twice")
    }

    @Test
    fun everySheetEntryIsActuallyBound() {
        // The claim the sheet makes to a viewer. An entry that fires nothing is
        // worse than a missing one: they press it and conclude the player is
        // broken.
        val plugin = handler()

        val unbound: List<String> = plugin.shortcuts().filterNot { plugin.handle(it) }.map { it.canonical }

        assertEquals(emptyList(), unbound, "the sheet lists combos that do nothing")
    }
}
