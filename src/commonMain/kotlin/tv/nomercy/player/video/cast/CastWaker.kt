// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import tv.nomercy.player.core.events.CastTarget

// What happened when a sleeping television was asked to wake.
//
// Five outcomes rather than a boolean, because the caller does different things
// with them: an already-awake set can be cast to immediately, a wake that was
// merely sent needs a moment, and no route at all is the one a viewer has to be
// told about.
public enum class WakeOutcome {
    AWOKE,
    WAKE_SENT,
    CAST_FALLBACK,
    NO_ROUTE,
    UNSUPPORTED,
}

// Waking a television panel so it can accept a cast.
//
// Wake-only, and that is the whole rule. The Android implementation talks to a
// Chromecast to turn the panel on and start the application, and it never loads
// media that way — the film reaches the set through the set's own player over
// /v1. Casting the video itself would put a second renderer in the picture,
// which is exactly what this subsystem exists to avoid.
//
// Desktop and Apple have no such path and do not need one: they reach the
// television directly and answer UNSUPPORTED.
public interface CastWaker {

    // Start looking for routes before anyone asks. Route discovery takes a
    // couple of seconds and a viewer who has just pressed cast should not spend
    // them watching a spinner.
    public suspend fun warmUp()

    public suspend fun wake(deviceId: String): WakeOutcome

    /**
     * The same wake, told which device it is for.
     *
     * An id means nothing to Cast, so a waker matching a route has only two
     * keys to match on: the address the device is at, and the name it
     * advertises. A sleeping television has no address — the server's mDNS
     * cannot see a box whose app is stopped, which is precisely when a wake is
     * the only way in — so the name has to be able to carry it alone.
     *
     * Defaulted to the id-only form, so a waker that cannot tell two receivers
     * apart keeps working and says so by not overriding this. A host with more
     * than one receiver on the network wants an implementation that does.
     */
    public suspend fun wake(target: CastTarget): WakeOutcome = wake(target.id)
}

// The waker for a platform that has no panel to wake.
public open class UnsupportedCastWaker : CastWaker {

    override suspend fun warmUp(): Unit = Unit

    override suspend fun wake(deviceId: String): WakeOutcome = WakeOutcome.UNSUPPORTED
}
