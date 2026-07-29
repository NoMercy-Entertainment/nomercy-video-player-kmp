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

// The width rule on Android, under a real Compose Android hierarchy.
//
// Both hosts rather than one, because the desktop host is where the width rule
// went unnoticed: it gives a test a window wide enough that nothing is ever
// dropped, so a bar ignoring the rule entirely looked the same as a bar obeying
// it. Robolectric's device is narrow, which makes it the host that notices.
@RunWith(RobolectricTestRunner::class)
// A wide device, because the bar drops controls that do not fit and
// Robolectric's default is 320dp — narrower than the chrome was ever
// designed for. Declared here rather than forced from inside the
// composition: a requiredWidth big enough to hold the bar puts it
// outside the window, where its nodes exist and cannot be touched.
@Config(sdk = [SDK_UNDER_TEST], qualifiers = "w1280dp-h720dp")
class ChromeWidthAndroidTest : ChromeWidthGate()

private const val SDK_UNDER_TEST = 34
