// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

// What a television does when something asks it to play. Platform-free — the
// same split [tv.nomercy.player.video.cast.TvControlClient] uses on the
// sender side, where the library owns the protocol and the application owns
// the wire. No transport reaches this class, only a sender id and a decoded
// [ReceiverCommand].
//
// The one rule this whole class exists for: a command from an unknown sender
// is refused rather than obeyed. A television that accepted whoever spoke
// last is a television anybody on the network can take over.
public class ReceiverSession(private val playback: ReceiverPlayback) {

    private var controllingSenderId: String? = null
    private var hasLoaded: Boolean = false

    public fun handle(senderId: String, command: ReceiverCommand): ReceiverOutcome {
        if (command is ReceiverCommand.Launch) return handleLaunch(senderId, command)

        val controller: String? = controllingSenderId
        return when {
            // A command arriving before anything is loaded. Nothing to seek,
            // pause or set a track on, and answering as though there were
            // would tell a sender its command worked when nothing moved.
            controller == null -> ReceiverOutcome.Refused(ReceiverRefusal.NOTHING_LOADED)

            // The one rule. A second sender's play/pause/seek is not this
            // session's to obey just because it arrived — only a fresh
            // Launch may take the session over, and only when nobody already
            // holds it.
            controller != senderId -> ReceiverOutcome.Refused(ReceiverRefusal.WRONG_SENDER)

            else -> {
                playback.apply(command)
                ReceiverOutcome.Accepted
            }
        }
    }

    private fun handleLaunch(senderId: String, command: ReceiverCommand.Launch): ReceiverOutcome {
        val controller: String? = controllingSenderId
        if (controller != null && controller != senderId) {
            return ReceiverOutcome.Refused(ReceiverRefusal.ALREADY_CONTROLLED)
        }

        if (!playback.canPlay(command.url)) {
            return ReceiverOutcome.Refused(ReceiverRefusal.CANNOT_PLAY)
        }

        controllingSenderId = senderId
        hasLoaded = true
        playback.apply(command)
        return ReceiverOutcome.Accepted
    }

    // The sender that was controlling this session is gone — its own stop,
    // or a disconnect the transport noticed. Only clears the session if this
    // sender is still the one holding it: a stale release from a sender that
    // already lost the session to a newer Launch must not evict whoever
    // holds it now.
    public fun release(senderId: String) {
        if (controllingSenderId != senderId) return
        controllingSenderId = null
        hasLoaded = false
    }

    public fun isControlledBy(senderId: String): Boolean = controllingSenderId == senderId

    // Whether hasLoaded is read anywhere yet, or exists only to be true once
    // a Launch lands — kept as a field regardless: the first real reconcile
    // frame this session sends will need to know whether it has anything to
    // report, and re-deriving that from controllingSenderId alone conflates
    // "controlled" with "loaded", which a session mid-Launch is neither.
    public fun hasContent(): Boolean = hasLoaded
}

// What actually plays, injected. A fake in tests, the application's real
// player in production — this class decides WHETHER a command runs, never
// how.
public interface ReceiverPlayback {

    public fun apply(command: ReceiverCommand)

    // True unless the device already knows better — a DRM scheme it does not
    // support, a codec it cannot decode. Defaulted true rather than abstract:
    // most hosts have nothing more specific to say than "yes" until they do.
    public fun canPlay(url: String): Boolean = true
}
