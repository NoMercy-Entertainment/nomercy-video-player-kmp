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
import androidx.media3.ui.AspectRatioFrameLayout
import tv.nomercy.player.video.Stretching
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

@Composable
public actual fun PlayerSurface(
    surface: VideoSurface,
    modifier: Modifier,
    stretching: Stretching,
) {
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
                player = surface.exoPlayer
            }
        },
        // Re-attached rather than rebuilt: a recomposition that dropped the
        // view would tear down the output and restart the video.
        update = { view ->
            view.player = surface.exoPlayer
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
