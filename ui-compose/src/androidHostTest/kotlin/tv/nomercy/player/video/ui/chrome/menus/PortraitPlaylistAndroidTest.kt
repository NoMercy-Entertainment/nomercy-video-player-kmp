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

// The portrait playlist on Android. The declared window is large enough for
// every size the gate mounts — each test sizes its own player box, and a window
// smaller than the box would clamp it and grade the window instead.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK_UNDER_TEST], qualifiers = "w1400dp-h1000dp")
class PortraitPlaylistAndroidTest : PortraitPlaylistGate()

private const val SDK_UNDER_TEST = 34
