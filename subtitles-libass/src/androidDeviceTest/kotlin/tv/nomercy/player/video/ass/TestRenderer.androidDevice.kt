// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import androidx.test.platform.app.InstrumentationRegistry

// The instrumentation's own context, because libass on Android reaches the
// system font directory through it.
internal actual fun newTestRenderer(): AssRenderer? =
    AssRenderers.create(
        AssPlatformContext(InstrumentationRegistry.getInstrumentation().targetContext),
    )
