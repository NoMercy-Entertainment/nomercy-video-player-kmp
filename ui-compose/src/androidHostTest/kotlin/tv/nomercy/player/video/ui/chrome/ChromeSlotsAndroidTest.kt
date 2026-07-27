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

// The override seam on Android.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK_UNDER_TEST])
class ChromeSlotsAndroidTest : ChromeSlotsGate()

private const val SDK_UNDER_TEST = 34
