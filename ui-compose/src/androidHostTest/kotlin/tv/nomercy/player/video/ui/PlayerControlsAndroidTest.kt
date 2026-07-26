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

// The gate on Android, where the host is a real Compose Android hierarchy under
// Robolectric. Same assertions, different toolkit underneath — which is the
// point of running it twice.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK_UNDER_TEST])
class PlayerControlsAndroidTest : PlayerControlsGate()

// Robolectric ships a sandbox per SDK level and downloads the one it is asked
// for. Naming it keeps CI from picking whatever is newest and failing on a
// platform this library does not claim to support yet.
private const val SDK_UNDER_TEST = 34
