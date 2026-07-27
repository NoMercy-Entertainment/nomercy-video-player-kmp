// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

// The system libass, where the machine has one. Windows does not: the only
// builds in circulation are statically linked inside VLC and mpv.
internal actual fun newTestRenderer(): AssRenderer? =
    AssRenderers.create(AssPlatformContext())
