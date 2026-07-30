// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome.menus

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The playlist pane on Android, under a real Compose Android hierarchy.
//
// Both hosts rather than one, for the reason ChromeWidthAndroidTest gives: the
// desktop host hands a test whatever window it likes, and a pane whose rails have
// no room looks the same there as one whose rails do. A declared device is what
// makes a width failure a failure.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK_UNDER_TEST], qualifiers = "w1280dp-h720dp")
class PlaylistPaneAndroidTest : PlaylistPaneGate()

private const val SDK_UNDER_TEST = 34
