// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

// Lines from an event stream, assembled into frames.
//
// Pulled out of the socket loop so it can be tested without one. This is the
// part with the states in it — a name held across lines, a payload that may
// arrive in several — and a bug here is a television whose events stop being
// understood halfway through an evening, which is not something a connected test
// finds quickly.
public open class SseAccumulator {

    private var eventName: String? = null

    private val data = StringBuilder()

    // A line in, a completed frame out when one is finished.
    //
    // Blank closes the block, which is the protocol: everything before it
    // belonged to one event and the next line starts another.
    public open fun feed(line: String): Pair<String, String>? {
        if (line.isEmpty()) return close()

        when {
            line.startsWith(EVENT_PREFIX) -> eventName = line.removePrefix(EVENT_PREFIX).trim()

            // Joined with a newline rather than concatenated. A payload split
            // across lines is one JSON document with its own line breaks, and
            // gluing the halves together makes a string that parses as nothing.
            line.startsWith(DATA_PREFIX) -> {
                val chunk: String = line.removePrefix(DATA_PREFIX).trim()
                if (data.isEmpty()) data.append(chunk) else data.append('\n').append(chunk)
            }
        }

        return null
    }

    private fun close(): Pair<String, String>? {
        if (data.isEmpty()) {
            // A blank line with nothing before it. Servers send these to keep a
            // connection from being closed by something in the middle, and
            // treating one as an empty frame would put a null state on screen
            // every fifteen seconds.
            eventName = null
            return null
        }

        val frame: Pair<String, String> = (eventName ?: DEFAULT_EVENT) to data.toString()
        eventName = null
        data.clear()
        return frame
    }

    private companion object {
        const val EVENT_PREFIX = "event:"
        const val DATA_PREFIX = "data:"

        // An unnamed frame is a state frame, which is what the television sends
        // most of and what the existing clients already assume.
        const val DEFAULT_EVENT = "state"
    }
}
