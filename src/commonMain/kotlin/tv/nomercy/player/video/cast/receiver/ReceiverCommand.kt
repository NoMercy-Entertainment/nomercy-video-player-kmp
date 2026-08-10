// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast.receiver

import tv.nomercy.player.video.cast.RemoteQualityLevel

// One decoded command, whatever it arrived as — the receiver's half of the
// same verbs [tv.nomercy.player.video.cast.TvControlClient] sends. Decoded
// rather than raw JSON: nothing here knows about a transport, so a websocket
// carrying the same verbs a REST route does answers to the same seam without
// this package changing.
public sealed interface ReceiverCommand {

    public data class Launch(val url: String, val autoplay: Boolean = true) : ReceiverCommand

    public data object Play : ReceiverCommand
    public data object Pause : ReceiverCommand
    public data object Stop : ReceiverCommand
    public data object Next : ReceiverCommand
    public data object Previous : ReceiverCommand

    public data class Seek(val positionMs: Long) : ReceiverCommand

    public data class SetVolume(
        val level: Double? = null,
        val delta: Double? = null,
        val muted: Boolean? = null,
    ) : ReceiverCommand

    public data class SetAudioTrack(val trackId: String) : ReceiverCommand

    public data class SetSubtitleTrack(val trackId: String?) : ReceiverCommand

    public data class SetQuality(val level: RemoteQualityLevel) : ReceiverCommand
}

// What the television answers, and why when it says no.
public sealed interface ReceiverOutcome {

    public data object Accepted : ReceiverOutcome

    public data class Refused(val reason: String) : ReceiverOutcome
}

// The reasons a command is refused, named rather than left as a free string —
// a sender showing a viewer why casting failed needs to switch on this, not
// pattern-match prose.
public object ReceiverRefusal {
    public const val CANNOT_PLAY: String = "cannot-play"
    public const val ALREADY_CONTROLLED: String = "already-controlled"
    public const val NOTHING_LOADED: String = "nothing-loaded"
    public const val WRONG_SENDER: String = "wrong-sender"
}
