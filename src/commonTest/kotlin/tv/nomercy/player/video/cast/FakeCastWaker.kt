// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

// A television that wakes, or does not, on request.
class FakeCastWaker(private val outcome: WakeOutcome = WakeOutcome.AWOKE) : CastWaker {

    val woke: MutableList<String> = mutableListOf()
    var warmUps: Int = 0
        private set

    override suspend fun warmUp() {
        warmUps += 1
    }

    override suspend fun wake(deviceId: String): WakeOutcome {
        woke += deviceId
        return outcome
    }
}
