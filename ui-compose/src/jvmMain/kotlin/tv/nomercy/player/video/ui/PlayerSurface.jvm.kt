// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters

@Composable
public actual fun PlayerSurface(surface: VideoSurface, modifier: Modifier) {
    val sink: ComposeFrameSink = remember(surface) { ComposeFrameSink() }

    DisposableEffect(surface, sink) {
        surface.embeddedPlayer.videoSurface().set(
            CallbackVideoSurface(sink, sink, true, VideoSurfaceAdapters.getVideoSurfaceAdapter()),
        )
        // Detached rather than left pointing at a sink whose composable is gone:
        // libVLC keeps decoding into whatever it was given, and a callback into
        // a disposed frame buffer is a crash in native code with a Kotlin stack
        // that explains nothing.
        onDispose { surface.embeddedPlayer.videoSurface().set(null) }
    }

    // Black, because the alternative is the window's background showing through
    // between the moment the view mounts and the first decoded frame.
    Box(modifier = modifier.background(Color.Black)) {
        val current: ImageBitmap? = sink.frame.value
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
