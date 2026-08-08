// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

// Skia is the desktop's imaging, and SkiaFrameSink is what this file's whole
// history is written in — the alpha type, the reuse, the wrap-don't-blit. A
// typealias rather than a second class so none of that moves.
internal actual typealias ComposeFrameSink = SkiaFrameSink
