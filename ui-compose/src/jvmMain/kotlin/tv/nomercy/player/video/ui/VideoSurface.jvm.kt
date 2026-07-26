// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import tv.nomercy.player.core.ports.VlcjVideoBackend
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

// libVLC renders into a native window handle, which on the desktop means an
// AWT component the embedded player is told about.
public actual class VideoSurface(public val embeddedPlayer: EmbeddedMediaPlayer)

// The one line an app writes to mount the view on the desktop.
public fun VideoSurface(backend: VlcjVideoBackend): VideoSurface =
    VideoSurface(backend.embeddedPlayer)
