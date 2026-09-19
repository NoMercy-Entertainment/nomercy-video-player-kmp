// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs [block] on this scope, then cancels the scope once it finishes.
 *
 * Cancelling [CoroutineScope.cancel] before [block] starts would cancel the
 * very launch carrying it, so the cancel is attached to the launched job's
 * own completion instead — [Job.invokeOnCompletion] fires on both a normal
 * return and a thrown failure, so a [block] that fails to finish still does
 * not leave this scope's job, and everything still reachable through it,
 * running for the rest of the process.
 *
 * Pulled out of AppleVideoEngine.dispose() so a host test can prove the
 * ordering without an AVFoundation-backed engine, which is Apple-only and
 * cannot build or run outside a macOS host.
 */
internal fun CoroutineScope.launchThenCancel(block: suspend () -> Unit): Job =
    launch { block() }.also { job: Job -> job.invokeOnCompletion { cancel() } }
