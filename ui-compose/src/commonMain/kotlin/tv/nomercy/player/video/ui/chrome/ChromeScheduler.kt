// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.nomercy.player.video.ui.tv.Cancellable
import tv.nomercy.player.video.ui.tv.Scheduler

// The autohide timer, on whatever scope the screen already has.
//
// The state machine takes a Scheduler rather than reaching for a coroutine
// itself, which is what lets its own tests run the four seconds instantly. This
// is the other half: the one a screen actually uses, cancelled with the screen
// because the scope it launches on is the composition's.
public class ChromeScheduler(private val scope: CoroutineScope) : Scheduler {

    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
        val job: Job = scope.launch {
            delay(delayMs)
            action()
        }

        return Cancellable { job.cancel() }
    }
}

@Composable
public fun rememberChromeScheduler(): Scheduler {
    val scope: CoroutineScope = rememberCoroutineScope()

    return remember(scope) { ChromeScheduler(scope) }
}
