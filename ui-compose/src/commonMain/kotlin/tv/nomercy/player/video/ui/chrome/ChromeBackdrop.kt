// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/**
 * The still behind the picture, until there is a picture.
 *
 * A port of the backdrop layer in `TrailerMobileUiPlugin`. Between mounting a
 * player and its first decoded frame there is a black rectangle, and on a
 * detail page that is a hole in the middle of the screen where a moment ago
 * there was artwork. His player fills it with the item's own image and fades it
 * out when playback starts.
 *
 * The image is the host's to draw. This module cannot fetch one — there is no
 * image loader in common code and picking one would put a networking library
 * into every consumer's build — so the slot takes a composable and this owns
 * only the two things that were actually getting lost: WHEN it is visible, and
 * the scrim over it.
 */
@Composable
public fun ChromeBackdrop(
    visible: Boolean,
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize().testTag(BACKDROP_TAG), contentAlignment = Alignment.Center) {
            image()

            // `Color.Black.copy(alpha = 0.3f)` over the still, as there. Without
            // it a bright backdrop makes white controls unreadable, and the
            // controls are drawn over this the whole time it is up.
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)))
        }
    }
}

/**
 * Whether the backdrop should be up, from the app's own condition.
 *
 * `duration <= 0 || time == 0` — not `!isPlaying`. The difference is the whole
 * point: a paused film has a frame on screen and needs no still behind it, while
 * a film that has been asked to play but has not decoded anything yet has
 * nothing, and that is the moment this covers. Reading it as "not playing" would
 * put artwork over the picture every time somebody paused.
 */
public fun backdropIsVisible(durationSeconds: Double, currentSeconds: Double): Boolean =
    durationSeconds <= 0.0 || currentSeconds == 0.0

internal const val BACKDROP_TAG = "nm-chrome-backdrop"

private const val SCRIM_ALPHA = 0.3f
