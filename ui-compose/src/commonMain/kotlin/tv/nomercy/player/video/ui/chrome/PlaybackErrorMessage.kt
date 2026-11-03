// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import tv.nomercy.player.core.errors.CoreErrorCodes

/** Viewer-facing text for the backend's raw error code. */
public object PlaybackErrorMessage {

    /** The sentence for a failure, by its code first and its text second. */
    public fun forError(code: String?, message: String?): String {
        val known: String? = code?.let { BY_CODE[it] }
        if (known != null) return known

        return byText(message.orEmpty().lowercase())
    }

    // A decoder error can mention the network that supplied its input.
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

    // Keep names here because Media3 is Android-only and this code is common.
    private val BY_CODE: Map<String, String> = mapOf(
        CoreErrorCodes.MEDIA_ABSENT to "The video source could not be loaded.",
        "ERROR_CODE_IO_UNSPECIFIED" to "This video cannot be played because of a problem with the source.",
        "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED" to NETWORK,
        "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT" to "Network timeout. Please check your internet connection.",
        "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE" to "Error loading media. The video source is not supported.",
        "ERROR_CODE_IO_BAD_HTTP_STATUS" to "The video source could not be loaded.",
        "ERROR_CODE_IO_FILE_NOT_FOUND" to "The video source could not be loaded.",
        "ERROR_CODE_PARSING_CONTAINER_MALFORMED" to CANNOT_PLAY,
        "ERROR_CODE_PARSING_MANIFEST_MALFORMED" to CANNOT_PLAY,
        "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED" to "Error loading media. The video source is not supported.",
        "ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED" to "Error loading media. The video source is not supported.",
        "ERROR_CODE_DECODER_INIT_FAILED" to "Decoder initialization failed. The video format may not be supported.",
        "ERROR_CODE_DECODER_QUERY_FAILED" to "Decoder query failed. The video codec may not be supported.",
        "ERROR_CODE_DECODING_FAILED" to "This video file cannot be played due to a decoder error.",
        "ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES" to "This video file cannot be played. The format exceeds device capabilities.",
        "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED" to CANNOT_PLAY,
        "ERROR_CODE_AUDIO_TRACK_INIT_FAILED" to "Audio rendering failed. The audio format may not be supported.",
        "ERROR_CODE_AUDIO_TRACK_WRITE_FAILED" to "Audio rendering failed. The audio format may not be supported.",
        "ERROR_CODE_DRM_UNSPECIFIED" to PROTECTED,
        "ERROR_CODE_DRM_SCHEME_UNSUPPORTED" to PROTECTED,
        "ERROR_CODE_DRM_PROVISIONING_FAILED" to "DRM provisioning failed.",
        "ERROR_CODE_DRM_CONTENT_ERROR" to "Content decryption failed.",
        "ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED" to "DRM license acquisition failed.",
    )
}
