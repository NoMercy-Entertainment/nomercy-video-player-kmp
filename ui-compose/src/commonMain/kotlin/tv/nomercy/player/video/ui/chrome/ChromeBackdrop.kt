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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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

/**
 * A failure the chrome can put on screen.
 *
 * The code and the raw text, kept apart. [PlaybackErrorMessage] turns them into
 * the sentence a viewer reads, and both are carried so the technical line and
 * the code can sit under it — his overlay shows all three, because a support
 * ticket that says "it did not work" costs an exchange of messages to turn into
 * "error 4003".
 */
public data class ChromeError(
    public val code: String?,
    public val technicalMessage: String?,
    public val fatal: Boolean = true,
) {

    /** What to tell the viewer, from the app's own table. */
    public val readable: String get() = PlaybackErrorMessage.forError(code, technicalMessage)
}

/**
 * The failure, on screen, in the words a viewer can act on.
 *
 * Three lines as his ErrorOverlay has them: the readable sentence, then the raw
 * text underneath when it says something different, then the code. The last two
 * look like clutter and are not — a support message reading "it did not work"
 * costs an exchange to turn into "error 4003", and the person who can read the
 * code off their own screen skips that entirely.
 */
@Composable
public fun ChromeErrorOverlay(error: ChromeError, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ERROR_GAP),
        modifier = modifier
            .background(ERROR_BACKGROUND, RoundedCornerShape(ERROR_RADIUS))
            .padding(ERROR_PADDING)
            .testTag(ERROR_TAG),
    ) {
        BasicText(text = error.readable, style = ERROR_HEADLINE)

        // Only when it adds something. A backend whose raw text is already the
        // sentence above would print it twice.
        error.technicalMessage
            ?.takeIf { it.isNotBlank() && it != error.readable }
            ?.let { BasicText(text = it, style = ERROR_DETAIL, modifier = Modifier.testTag(ERROR_DETAIL_TAG)) }

        error.code
            ?.takeIf { it.isNotBlank() }
            ?.let { BasicText(text = it, style = ERROR_CODE, modifier = Modifier.testTag(ERROR_CODE_TAG)) }
    }
}

internal const val ERROR_TAG = "nm-chrome-error"
internal const val ERROR_DETAIL_TAG = "nm-chrome-error-detail"
internal const val ERROR_CODE_TAG = "nm-chrome-error-code"

private val ERROR_BACKGROUND = Color(red = 20, green = 20, blue = 25, alpha = 242)
private val ERROR_RADIUS = 16.dp
private val ERROR_PADDING = 32.dp
private val ERROR_GAP = 8.dp

private val ERROR_HEADLINE = TextStyle(
    color = Color.White,
    fontSize = 18.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.Center,
)

private val ERROR_DETAIL = TextStyle(
    color = Color.White.copy(alpha = 0.7f),
    fontSize = 11.sp,
    textAlign = TextAlign.Center,
)

private val ERROR_CODE = TextStyle(
    color = Color.White.copy(alpha = 0.7f),
    fontSize = 14.sp,
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Center,
)
