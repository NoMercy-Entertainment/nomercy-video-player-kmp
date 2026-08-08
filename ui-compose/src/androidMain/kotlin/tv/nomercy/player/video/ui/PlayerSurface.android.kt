// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import tv.nomercy.player.video.Stretching

/**
 * Draws whichever engine the device got.
 *
 * Media3 renders itself and is mounted as a view; libmpv decodes into a buffer
 * and its frames are drawn by the same canvas the desktop uses. The branch is
 * on what the surface HOLDS rather than on a flag, so an engine that produces
 * frames cannot be mounted into a view that expects a player.
 */
@Composable
public actual fun PlayerSurface(
    surface: VideoSurface,
    modifier: Modifier,
    stretching: Stretching,
) {
    val player = surface.exoPlayer
    val sink = surface.sink

    if (player == null && sink != null) {
        // The measured size goes to the engine on every layout pass, and it is
        // what caps the ladder — an engine rendering into a buffer has no view
        // to measure itself against. Compose reports layout in device pixels
        // already, so there is no density factor to apply the way the web
        // multiplies by devicePixelRatio.
        FrameCanvas(
            sink = sink,
            modifier = modifier.onSizeChanged { size ->
                surface.backend?.surfaceSize(size.width, size.height)
            },
            scale = contentScaleOf(stretching),
        )
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                // The library draws the video and nothing else. Media3's own
                // controller would be a second set of controls fighting the
                // one above it, and an app replacing this view's chrome would
                // have to find and disable it.
                useController = false
                resizeMode = resizeModeOf(stretching)
                // PlayerView keeps an opaque black shutter over the video and
                // lifts it when ITS OWN output reports a frame. That is a
                // different signal from the engine's, and when the two disagree
                // the result is the exact report: firstFrame fired, the engine
                // listed three audio tracks, six subtitles and nine rungs, and
                // the screen stayed black. A transparent shutter cannot hide a
                // picture that is being decoded.
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        // Re-attached rather than rebuilt: a recomposition that dropped the
        // view would tear down the output and restart the video.
        update = { view ->
            view.player = player
            view.resizeMode = resizeModeOf(stretching)
            view.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        },
        onRelease = { view -> view.player = null },
    )
}

// The web's object-fit values, as Media3 spells them.
//
// FIT is contain, FILL is fill, ZOOM is cover. `none` has no counterpart — a
// PlayerView always scales — so it lands on FIT, which is the value that alters
// the picture least.
private fun resizeModeOf(stretching: Stretching): Int = when (stretching) {
    Stretching.Uniform, Stretching.None -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    Stretching.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    Stretching.ExactFit -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}

// The same values as Compose spells them, for the engine that has no view to
// hand them to. None draws the frame at its own size rather than scaling it.
private fun contentScaleOf(stretching: Stretching): ContentScale = when (stretching) {
    Stretching.Uniform -> ContentScale.Fit
    Stretching.None -> ContentScale.None
    Stretching.Fill -> ContentScale.FillBounds
    Stretching.ExactFit -> ContentScale.Crop
}
