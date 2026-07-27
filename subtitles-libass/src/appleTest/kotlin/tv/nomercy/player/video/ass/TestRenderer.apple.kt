// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

// Statically linked through cinterop, so it is always present on iOS and tvOS.
internal actual fun newTestRenderer(): AssRenderer? =
    AssRenderers.create(AssPlatformContext())
