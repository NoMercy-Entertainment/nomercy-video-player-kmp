// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The bar's expanded/collapsed semantics on Android, where TalkBack is the
// reader that consumes them.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK_UNDER_TEST], qualifiers = "w1280dp-h720dp")
class MenuTriggerAndroidTest : MenuTriggerGate()

private const val SDK_UNDER_TEST = 34
