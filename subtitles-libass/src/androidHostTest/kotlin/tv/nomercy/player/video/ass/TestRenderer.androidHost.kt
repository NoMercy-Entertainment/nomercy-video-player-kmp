// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

// None, and not for want of trying. The binding loads its native library in a
// static initializer, which on a host JVM fails before any of it exists — the
// libass gates for Android run on hardware, where a decoder and a font provider
// are real.
internal actual fun newTestRenderer(): AssRenderer? = null
