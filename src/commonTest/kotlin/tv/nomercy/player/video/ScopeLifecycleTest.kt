// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// AppleVideoEngine.dispose() used to `scope.launch { player.dispose() }` and
// never cancel [scope] at all — on the real Apple path this leaves the
// scope's own Job, and every released AVFoundation reference still closed
// over by it, reachable for the rest of the process's life. This is the
// platform-independent half of that fix: the ordering (cancel only AFTER the
// launched work finishes) rather than the AVFoundation-specific wiring,
// which is Apple-only and cannot build or run outside a macOS host.
class ScopeLifecycleTest {

    @Test
    fun theScopeIsCancelledAfterTheLaunchedWorkFinishes() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var ranBeforeCancel = false

        scope.launchThenCancel {
            ranBeforeCancel = scope.isActive
        }

        assertTrue(ranBeforeCancel, "the block must run while the scope is still active")
        assertFalse(scope.isActive, "the scope must be cancelled once the launched work finishes")
    }
}
