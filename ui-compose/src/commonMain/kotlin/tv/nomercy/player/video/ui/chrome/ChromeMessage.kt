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
import androidx.compose.runtime.Stable
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
    val channel: ChromeMessageChannel = remember { ChromeMessageChannel() }

    DisposableEffect(player, strings) {
        // At mount, because the player is already fetching. The web calls
        // showBuffer('message.loading') from wireFeedback itself rather than
        // waiting for an event, and without it the first thing a viewer sees is
        // an empty black frame with nothing explaining it.
        channel.feedback(strings.loading)

        val subscriptions: List<Subscription> =
            channel.subscribeToPlayback(player, strings) + channel.subscribeToNotices(player, strings)

        onDispose { subscriptions.forEach(Subscription::dispose) }
    }

    // Keyed on the message as well as the delay: two identical requests in a row
    // are two notices, and keying on the duration alone would let the second ride
    // out the first one's remaining time.
    val after: Double? = channel.expiresAfterMs

    if (after != null) {
        LaunchedEffect(channel.message, after) {
            delay(after.toLong())
            channel.clear()
        }
    }

    return channel.message
}

// The message channel as one object rather than two pieces of composition state
// and six closures over them. What a subscription does to the channel is then a
// named call, which is what lets the wiring below read as a list of events.
@Stable
internal class ChromeMessageChannel {

    public var message: ChromeMessage? by mutableStateOf(null)
        private set

    // A countdown that only exists while a timed message is up. Held as state so
    // a new message replaces the previous one's timer instead of racing it — the
    // web clears its handle before setting another for the same reason.
    public var expiresAfterMs: Double? by mutableStateOf(null)
        private set

    fun feedback(text: String) {
        message = ChromeMessage(text, ChromeMessage.Kind.Feedback)
        expiresAfterMs = null
    }

    // A notice that clears itself. The feedback channel waits for a Playing or a
    // Time tick, and neither follows a volume press.
    fun timed(text: String) {
        message = ChromeMessage(text, ChromeMessage.Kind.Host)
        expiresAfterMs = TIMED_MESSAGE_MS
    }

    fun host(text: String, ms: Double?) {
        message = ChromeMessage(text, ChromeMessage.Kind.Host)
        expiresAfterMs = ms
    }

    fun failure(text: String) {
        message = ChromeMessage(text, ChromeMessage.Kind.Failure)
        expiresAfterMs = null
    }

    // Only the player's own notices. A host's message stays up.
    fun clearFeedback() {
        if (message?.kind == ChromeMessage.Kind.Feedback) {
            message = null
        }
    }

    fun clear() {
        message = null
        expiresAfterMs = null
    }

    // What the player is doing: arriving, waiting, running, failing.
    fun subscribeToPlayback(player: NMVideoPlayer, strings: TvChromeStrings): List<Subscription> = listOf(
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
        player.on(CoreEvents.Error) { failure(strings.error) },
    )

    // What somebody just changed, and what a host asked to say.
    //
    // The volume and mute notices are the three the web shows and this channel
    // never carried: the browser says so on every press — `showMessage(
    // t('message.volume', { level }), 1200)` and the muted/unmuted pair beside
    // it. Timed rather than feedback, because they expire on their own instead
    // of waiting for a Playing or a Time tick that a volume change never fires.
    //
    // A track change is named for the same reason, and the gap was worse: a
    // styled subtitle is fetched, then its fonts, then rasterised, so selecting
    // one showed nothing at all until the first cue arrived seconds later.
    fun subscribeToNotices(player: NMVideoPlayer, strings: TvChromeStrings): List<Subscription> = listOf(
        player.on(VideoEvents.DisplayMessage) { asked ->
            host(asked.text, asked.ms?.takeIf { it > 0.0 })
        },
        player.on(VideoEvents.RemoveMessage) { clear() },
        player.on(CoreEvents.Volume) { change ->
            timed(strings.volumeMessage.replace(LEVEL_TOKEN, change.level.toString()))
        },
        player.on(CoreEvents.Mute) { change ->
            timed(if (change.muted) strings.mutedMessage else strings.unmutedMessage)
        },
        player.on(CoreEvents.Subtitle) { change ->
            timed(trackMessage(strings.subtitles, player.subtitles().map { it.label }, change.track, strings.offValue))
        },
        player.on(CoreEvents.AudioTrack) { change ->
            timed(trackMessage(strings.language, player.audioTracks().map { it.label }, change.id, strings.offValue))
        },
    )
}

/**
 * "Subtitles: English (Full)", the shape the volume notice already uses.
 *
 * Both track events carry an INDEX rather than an id — `ComposedPlayer` emits
 * `indexIn(audioTracks(), track)` — and a null or out-of-range one is the track
 * being turned off.
 */
internal fun trackMessage(kind: String, labels: List<String>, index: Double?, off: String): String =
    "$kind: ${index?.toInt()?.let(labels::getOrNull) ?: off}"

// The web's `{level}` placeholder, and its 1200ms for a notice that says what
// just happened rather than what is happening.
private const val LEVEL_TOKEN = "{level}"
private const val TIMED_MESSAGE_MS = 1_200.0
