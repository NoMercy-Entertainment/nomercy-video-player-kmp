// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

// F15 on real hardware: wake() against real GMS Cast APIs, no mock.
class ChromecastCastWakerDeviceTest {

    @Test
    fun wakingWithNoRealCastTargetDoesNotCrashTheProcess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val waker = ChromecastCastWaker(context)

        val outcome: WakeOutcome = runBlocking { waker.wake("no-such-device") }

        println("F15 device trace: ranWithoutCrashing=true outcome=$outcome")
    }
}
