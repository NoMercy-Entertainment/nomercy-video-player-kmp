// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.Subscription
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.VideoEvents
import tv.nomercy.player.video.ui.tv.TvChromeStrings
import kotlinx.coroutines.delay

/**
 * What the player is telling the viewer right now, and for how long.
 *
 * The whole of `wireFeedback`. This side subscribed to two of its nine events —
 * display-message and remove-message — so the chrome said nothing while an item
 * loaded, nothing while a stream stalled, and nothing when a decode failed. The
 * spinner-shaped hole in the middle of a buffering player was the visible half;
 * the invisible half is that `ms` on DisplayMessage was accepted and dropped, so
 * a host asking for a two-second notice got a permanent one.
 *
 * [kind] is the web's `messageIsFeedback`, and it is not bookkeeping. `playing`
 * and `time` clear a BUFFERING notice, because those events mean the buffering
 * ended. They must not clear a message the host asked for — that one is somebody
 * else's sentence, and playback resuming is not a reason to cut it off.
 */
public data class ChromeMessage(
    val text: String,
    val kind: Kind,
) {
    public enum class Kind {
        /** The player's own report: loading, buffering, a failure. */
        Feedback,

        /** The host asked for these words to be on screen. */
        Host,
    }
}

/**
 * Subscribes the chrome to every message the web's plugin listens for.
 *
 * Buffering is reported as a message rather than only as a spinner because that
 * is what the web does, and because "Loading" and "Buffering" are different
 * sentences: the first is the item arriving and the second is it running out
 * mid-play, and a viewer on a slow connection can tell which is happening.
 */
@Composable
public fun rememberChromeMessage(player: NMVideoPlayer, strings: TvChromeStrings): ChromeMessage? {
    var message: ChromeMessage? by remember { mutableStateOf(null) }

    // A countdown that only exists while a timed message is up. Held as state so
    // a new message replaces the previous one's timer instead of racing it — the
    // web clears its handle before setting another for the same reason.
    var expiresAfterMs: Double? by remember { mutableStateOf(null) }

    DisposableEffect(player, strings) {
        fun feedback(text: String) {
            message = ChromeMessage(text, ChromeMessage.Kind.Feedback)
            expiresAfterMs = null
        }

        // Only the player's own notices. A host's message stays up.
        fun clearFeedback() {
            if (message?.kind == ChromeMessage.Kind.Feedback) {
                message = null
            }
        }

        // At mount, because the player is already fetching. The web calls
        // showBuffer('message.loading') from wireFeedback itself rather than
        // waiting for an event, and without it the first thing a viewer sees is
        // an empty black frame with nothing explaining it.
        feedback(strings.loading)

        val subscriptions: List<Subscription> = listOf(
            player.on(VideoEvents.Waiting) { feedback(strings.buffering) },
            player.on(VideoEvents.Stalled) { feedback(strings.buffering) },
            player.on(CoreEvents.Item) { feedback(strings.loading) },
            player.on(CoreEvents.Playing) { clearFeedback() },
            player.on(CoreEvents.Time) { clearFeedback() },
            player.on(CoreEvents.Error) { feedback(strings.error) },
            player.on(VideoEvents.DisplayMessage) { asked ->
                message = ChromeMessage(asked.text, ChromeMessage.Kind.Host)
                expiresAfterMs = asked.ms?.takeIf { it > 0.0 }
            },
            player.on(VideoEvents.RemoveMessage) {
                message = null
                expiresAfterMs = null
            },
        )

        onDispose { subscriptions.forEach(Subscription::dispose) }
    }

    // Keyed on the message as well as the delay: two identical requests in a row
    // are two notices, and keying on the duration alone would let the second ride
    // out the first one's remaining time.
    val after: Double? = expiresAfterMs

    if (after != null) {
        LaunchedEffect(message, after) {
            delay(after.toLong())
            message = null
            expiresAfterMs = null
        }
    }

    return message
}
