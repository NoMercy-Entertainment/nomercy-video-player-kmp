// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.player.PlayerPhase
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

        /**
         * Playback failed, and this does NOT clear on a time tick.
         *
         * An unsupported video codec keeps its audio running, so `Time` kept
         * firing and wiped the failure the instant it was reported — a black
         * picture with no explanation, measured on an SM-A137F.
         */
        Failure,
    }
}

/**
 * Whether a wait for data is worth telling the viewer about.
 *
 * Playing was the only thing that took the buffering notice down, and a viewer
 * who pauses and then seeks gets a Waiting for the seek with no Playing after
 * it, because the player is never going to start. The notice then sat on a
 * still frame for as long as they left it there. A paused player is not waiting
 * for data, it is waiting for them.
 */
internal fun waitIsWorthAnnouncing(phase: PlayerPhase): Boolean = phase != PlayerPhase.PAUSED

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

        // A notice that clears itself. The feedback channel waits for a Playing
        // or a Time tick, and neither follows a volume press.
        fun timed(text: String) {
            message = ChromeMessage(text, ChromeMessage.Kind.Host)
            expiresAfterMs = TIMED_MESSAGE_MS
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
            player.on(VideoEvents.Waiting) {
                if (waitIsWorthAnnouncing(player.phase())) feedback(strings.buffering)
            },
            player.on(VideoEvents.Stalled) {
                if (waitIsWorthAnnouncing(player.phase())) feedback(strings.buffering)
            },
            player.on(CoreEvents.Item) { feedback(strings.loading) },
            player.on(CoreEvents.Playing) { clearFeedback() },
            player.on(CoreEvents.Time) { clearFeedback() },
            // And a pause takes down whatever is already up.
            player.on(CoreEvents.Pause) { clearFeedback() },
            // Enough data to play, whether or not anybody pressed play. A viewer
            // who never presses play — the pre-screen's still-paused preview —
            // gets no Playing and no Time, so without this a load that finished
            // during that wait kept "Buffering" over a fully ready picture.
            player.on(CoreEvents.Ready) { clearFeedback() },
            player.on(CoreEvents.Error) {
                message = ChromeMessage(strings.error, ChromeMessage.Kind.Failure)
                expiresAfterMs = null
            },
            player.on(VideoEvents.DisplayMessage) { asked ->
                message = ChromeMessage(asked.text, ChromeMessage.Kind.Host)
                expiresAfterMs = asked.ms?.takeIf { it > 0.0 }
            },
            player.on(VideoEvents.RemoveMessage) {
                message = null
                expiresAfterMs = null
            },
            // The three the web shows and this channel never carried. A viewer
            // changing the volume or muting saw nothing at all here while the
            // browser says so on every press — `showMessage(t('message.volume',
            // { level }), 1200)` and the muted/unmuted pair beside it.
            //
            // Timed rather than feedback: these expire on their own after 1200ms
            // instead of waiting for a Playing or a Time tick to clear them, and
            // a volume change does not produce either.
            player.on(CoreEvents.Volume) { change ->
                timed(strings.volumeMessage.replace(LEVEL_TOKEN, change.level.toString()))
            },
            player.on(CoreEvents.Mute) { change ->
                timed(if (change.muted) strings.mutedMessage else strings.unmutedMessage)
            },

            // A track change, named. Master says so on every audio and subtitle
            // switch and this channel carried neither, so selecting a styled
            // subtitle — fetched, then its fonts, then rasterised — showed
            // nothing at all until the first cue arrived seconds later.
            player.on(CoreEvents.Subtitle) { change ->
                timed(trackMessage(strings.subtitles, player.subtitles(), change.track, strings.offValue) { it.label })
            },
            player.on(CoreEvents.AudioTrack) { change ->
                timed(trackMessage(strings.language, player.audioTracks(), change.id, strings.offValue) { it.label })
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

/**
 * "Subtitles: English (Full)", the shape the volume notice already uses.
 *
 * Both track events carry an INDEX rather than an id — `ComposedPlayer` emits
 * `indexIn(audioTracks(), track)` — and a null or out-of-range one is the track
 * being turned off.
 */
internal fun <T> trackMessage(kind: String, tracks: List<T>, index: Double?, off: String, name: (T) -> String): String =
    "$kind: ${index?.toInt()?.let(tracks::getOrNull)?.let(name) ?: off}"

// The web's `{level}` placeholder, and its 1200ms for a notice that says what
// just happened rather than what is happening.
private const val LEVEL_TOKEN = "{level}"
private const val TIMED_MESSAGE_MS = 1_200.0
