// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

/**
 * What to tell a viewer when playback fails, from the app's own table.
 *
 * A port of `getUserFriendlyMessage` in ErrorOverlay.kt, code for code and
 * sentence for sentence. This is the part of an error overlay worth porting
 * carefully: the box around it is a box, and what decides whether somebody
 * understands what went wrong is whether error 4003 says the format exceeds
 * what the device can do or says "PlaybackException 4003".
 *
 * Twenty codes in five ranges, and a fallback that reads the message text when
 * the code is one this table has never seen. The fallback matters more than it
 * looks: a backend that reports a decoder failure without a numeric code still
 * has "codec" in its message, and matching on that is the difference between a
 * sentence and a stack trace.
 *
 * Separate from any composable so it can be checked without a screen. It is a
 * lookup, and a lookup that has drifted is invisible in a screenshot — every
 * message still appears, each one just describes the wrong failure.
 */
public object PlaybackErrorMessage {

    /** The sentence for a failure, by its code first and its text second. */
    public fun forError(code: String?, message: String?): String {
        val known: String? = BY_CODE[code?.toIntOrNull()]
        if (known != null) return known

        return byText(message.orEmpty().lowercase())
    }

    // The message text, when the code is absent or unrecognised. Order is the
    // app's: the earlier arms are the more specific failures, and a message
    // mentioning both a codec and the network is a codec problem.
    private fun byText(message: String): String = when {
        message.containsAny("exceeds_capabilities", "codec", "decoder") -> CANNOT_PLAY
        message.containsAny("network", "timeout", "connection") -> NETWORK
        message.containsAny("format", "unsupported", "invalid") -> CANNOT_PLAY
        message.containsAny("drm", "license", "decrypt") -> PROTECTED
        message.containsAny("source", "no media") -> CANNOT_PLAY
        else -> UNKNOWN
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { contains(it) }

    private const val CANNOT_PLAY = "This video file cannot be played."
    private const val NETWORK =
        "This video cannot be played because of a problem with your internet connection."
    private const val PROTECTED =
        "There was a problem providing access to protected content."
    private const val UNKNOWN = "Unknown error"

    // Media3's PlaybackException.ERROR_CODE_* values, grouped as the app groups
    // them. Written out rather than derived, because the numbers are Media3's
    // and this module cannot depend on Media3 — a desktop or an Apple build has
    // no such class and would not compile.
    private val BY_CODE: Map<Int, String> = mapOf(
        // Source, 1xxx.
        1000 to "This video cannot be played because of a problem with the source.",
        1001 to "Error loading media. The video source is not supported.",
        1002 to "This video cannot be played because the source is behind CORS.",
        1003 to "The video source could not be loaded.",

        // Network, 2xxx.
        2000 to NETWORK,
        2001 to "Network timeout. Please check your internet connection.",
        2002 to "This video cannot be played because of a network error.",

        // Decoder, 4xxx.
        4001 to "This video file cannot be played due to a decoder error.",
        4002 to "Decoder initialization failed. The video format may not be supported.",
        4003 to "This video file cannot be played. The format exceeds device capabilities.",
        4004 to "Decoder query failed. The video codec may not be supported.",

        // Renderer, 5xxx.
        5001 to "Video rendering failed. The video may be corrupted.",
        5002 to "Audio rendering failed. The audio format may not be supported.",
        5003 to "This video file cannot be played due to a text rendering error.",

        // DRM, 6xxx.
        6000 to PROTECTED,
        6001 to "DRM license acquisition failed.",
        6002 to "DRM provisioning failed.",
        6003 to "Content decryption failed.",
    )
}
