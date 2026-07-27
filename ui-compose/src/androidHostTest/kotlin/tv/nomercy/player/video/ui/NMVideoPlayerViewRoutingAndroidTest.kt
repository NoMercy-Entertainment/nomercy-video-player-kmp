// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The routing on Android, which is the host that actually has all four form
// factors: the same build runs on a phone, a tablet and a television.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK_UNDER_TEST])
class NMVideoPlayerViewRoutingAndroidTest : NMVideoPlayerViewRoutingGate()

private const val SDK_UNDER_TEST = 34
