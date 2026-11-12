// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One bounded scan: [start] adds a token, and it is removed once after [windowMs].
 * A [start] inside the window does nothing; one after it begins a fresh scan.
 */
internal class WarmScan<C : Any>(
    private val scope: CoroutineScope,
    private val windowMs: Long,
    private val create: () -> C?,
    private val add: (C) -> Unit,
    private val remove: (C) -> Unit,
) {
    private var live: C? = null

    fun start() {
        if (live != null) return
        val token = create() ?: return
        add(token)
        live = token
        scope.launch {
            delay(windowMs)
            live = null
            remove(token)
        }
    }
}
