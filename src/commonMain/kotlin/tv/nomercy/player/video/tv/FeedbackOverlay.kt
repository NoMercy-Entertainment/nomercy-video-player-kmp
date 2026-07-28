// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

/**
 * The message the player shows over itself, and the spinner behind it.
 *
 * From `desktop-ui/mixins/feedbackMethods.ts`. It looks like "show some text"
 * and is really a small state machine with one rule that decides whether the
 * player feels responsive or feels stuck:
 *
 * **A message the player raised about itself is cleared by playback resuming.
 * A message somebody else asked for is not.** That is `messageIsFeedback`. Miss
 * it and one of two things happens: either a caller's message is wiped the
 * instant the next time update lands, or a "Buffering" that has finished
 * buffering stays on screen over moving video.
 */
public enum class FeedbackKind {
    /** The player talking about itself: loading, buffering, error. */
    Feedback,

    /** Somebody else's message, raised through `display-message`. */
    Message,
}

/**
 * [text] empty means nothing is shown. [buffering] drives the spinner, which is
 * separate from the text: the web adds a `buffering` class to the container and
 * removes it on error while leaving the error text up.
 */
public data class FeedbackState(
    val text: String = "",
    val kind: FeedbackKind = FeedbackKind.Feedback,
    val buffering: Boolean = false,
    /** Milliseconds until this hides itself; null means it stays. */
    val hideAfterMs: Long? = null,
) {
    public val visible: Boolean get() = text.isNotEmpty()
}

/** What the player did, as far as the overlay is concerned. */
public sealed interface FeedbackEvent {
    /** The player is mounting media. The web shows this before anything else. */
    public data object Mounted : FeedbackEvent

    public data object Waiting : FeedbackEvent

    public data object Stalled : FeedbackEvent

    /** A new item started loading. */
    public data object ItemChanged : FeedbackEvent

    public data object Playing : FeedbackEvent

    /** A time update. Clears feedback the same way `playing` does. */
    public data object Progressed : FeedbackEvent

    public data object Failed : FeedbackEvent

    /** Somebody else's message. [ms] null keeps it up until removed. */
    public data class Display(val text: String, val ms: Long? = null) : FeedbackEvent

    public data object Remove : FeedbackEvent
}

/**
 * The strings the overlay needs, localised by the caller.
 *
 * Four keys, matching `message.loading`, `message.buffering` and
 * `message.error`.
 */
public data class FeedbackStrings(
    val loading: String,
    val buffering: String,
    val error: String,
)

/** The next overlay state. Pure, so the rule above is testable without a player. */
public fun nextFeedbackState(
    current: FeedbackState,
    event: FeedbackEvent,
    strings: FeedbackStrings,
): FeedbackState = when (event) {
    FeedbackEvent.Mounted, FeedbackEvent.ItemChanged -> buffer(strings.loading)

    FeedbackEvent.Waiting, FeedbackEvent.Stalled -> buffer(strings.buffering)

    // The spinner always stops. The text only clears if the player is the one
    // who put it there — a caller's message outlives playback resuming.
    FeedbackEvent.Playing, FeedbackEvent.Progressed ->
        if (current.kind == FeedbackKind.Feedback) {
            FeedbackState()
        } else {
            current.copy(buffering = false)
        }

    // An error stops the spinner and keeps the text. Leaving the spinner
    // running under an error message reads as "still trying", which it is not.
    FeedbackEvent.Failed -> FeedbackState(
        text = strings.error,
        kind = FeedbackKind.Feedback,
        buffering = false,
    )

    is FeedbackEvent.Display -> FeedbackState(
        text = event.text,
        kind = FeedbackKind.Message,
        buffering = current.buffering,
        // A zero or negative duration is not a request to hide immediately;
        // the web's `ms > 0` check treats it as no timer at all.
        hideAfterMs = event.ms?.takeIf { it > 0 },
    )

    FeedbackEvent.Remove -> FeedbackState(buffering = current.buffering)
}

private fun buffer(text: String): FeedbackState = FeedbackState(
    text = text,
    kind = FeedbackKind.Feedback,
    buffering = true,
)
