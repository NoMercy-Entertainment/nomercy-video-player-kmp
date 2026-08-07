// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.input.KeyHandlerScope
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.video.input.VideoKeyHandlerPlugin
import tv.nomercy.player.video.input.playerCommandsOf
import tv.nomercy.player.video.NMVideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which scope the key handler runs in, and what that means for delivery.
 *
 * The reference's `scope` defaults to `'document'` — a listener on the whole
 * page, firing whether or not anything in the player has focus. The port had no
 * such option and behaved as `'container'` always, because its only delivery
 * was `Modifier.onKeyEvent` on the chrome's focusable. Clicking any control
 * outside the player took focus away and every shortcut stopped: space did not
 * pause a playing film, proven against the running desktop app.
 */
class WindowScopeKeysTest {

    // Through the real builder rather than a hand-made command set: the point
    // of the test is the scope, and a stand-in that drifted from the shipped
    // commands would pass while the shipped ones did nothing.
    private fun commands() =
        playerCommandsOf(NMVideoPlayer(RecordingVideoBackend()), CoroutineScope(Dispatchers.Default))

    private fun handler(scope: KeyHandlerScope) = VideoKeyHandlerPlugin(
        commands = commands(),
        capabilities = ChromeCapabilities(FormFactor.Desktop),
        nowMs = { 0L },
        scope = scope,
    )

    @Test
    fun theDefaultIsTheWholeWindow() {
        // The reference's default, and the whole point: a shortcut that stops
        // working because a button was clicked is the defect this closes.
        assertEquals(KeyHandlerScope.Window, handler(KeyHandlerScope.Window).scope)
        assertEquals(
            KeyHandlerScope.Window,
            VideoKeyHandlerPlugin(
                commands = commands(),
                capabilities = ChromeCapabilities(FormFactor.Desktop),
                nowMs = { 0L },
            ).scope,
        )
    }

    @Test
    fun aContainerScopedHandlerStillBinds() {
        // Both scopes share one table; only delivery differs. A container-scoped
        // handler that had lost its bindings would pass a delivery test and fail
        // a consumer.
        val keys = handler(KeyHandlerScope.Container)
        keys.use()

        assertTrue(keys.bindings().isNotEmpty(), "the default bindings did not install")
    }
}
