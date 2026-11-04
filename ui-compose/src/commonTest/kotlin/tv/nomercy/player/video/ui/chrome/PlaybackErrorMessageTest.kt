// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlaybackErrorMessageTest {

    @Test
    fun androidDecoderCodeNameDescribesTheDeviceLimit() {
        assertEquals(
            "This video file cannot be played. The format exceeds device capabilities.",
            PlaybackErrorMessage.forError("ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES", "PlaybackException"),
        )
    }

    @Test
    fun androidNetworkTimeoutCodeNameDescribesTheTimeout() {
        assertEquals(
            "Network timeout. Please check your internet connection.",
            PlaybackErrorMessage.forError("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT", "PlaybackException"),
        )
    }

    @Test
    fun missingMediaCodeDescribesTheSource() {
        assertEquals(
            "The video source could not be loaded.",
            PlaybackErrorMessage.forError("core:stream/media-absent", "PlaybackException"),
        )
    }

    @Test
    fun aDrmCodeSaysProtectedRatherThanBroken() {
        assertEquals(
            "DRM license acquisition failed.",
            PlaybackErrorMessage.forError("ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED", null),
        )
    }

    @Test
    fun knownBackendCodeNamesDoNotFallThrough() {
        val codes = listOf(
            "core:stream/media-absent",
            "ERROR_CODE_IO_UNSPECIFIED",
            "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
            "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE",
            "ERROR_CODE_IO_BAD_HTTP_STATUS",
            "ERROR_CODE_IO_FILE_NOT_FOUND",
            "ERROR_CODE_PARSING_CONTAINER_MALFORMED",
            "ERROR_CODE_PARSING_MANIFEST_MALFORMED",
            "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED",
            "ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED",
            "ERROR_CODE_DECODER_INIT_FAILED",
            "ERROR_CODE_DECODER_QUERY_FAILED",
            "ERROR_CODE_DECODING_FAILED",
            "ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES",
            "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED",
            "ERROR_CODE_AUDIO_TRACK_INIT_FAILED",
            "ERROR_CODE_AUDIO_TRACK_WRITE_FAILED",
            "ERROR_CODE_DRM_UNSPECIFIED",
            "ERROR_CODE_DRM_SCHEME_UNSUPPORTED",
            "ERROR_CODE_DRM_PROVISIONING_FAILED",
            "ERROR_CODE_DRM_CONTENT_ERROR",
            "ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED",
        )

        codes.forEach { code ->
            assertNotEquals("Unknown error", PlaybackErrorMessage.forError(code, null), code)
        }
    }

    @Test
    fun aCodelessDecoderFailureIsStillReadAsOne() {
        assertEquals(
            "This video file cannot be played.",
            PlaybackErrorMessage.forError(null, "MediaCodec decoder init failed"),
        )
    }

    @Test
    fun aCodelessNetworkFailureBlamesTheConnection() {
        assertEquals(
            "This video cannot be played because of a problem with your internet connection.",
            PlaybackErrorMessage.forError(null, "Connection reset by peer"),
        )
    }

    // A decoder can fail while reading from a healthy network stream.
    @Test
    fun theMoreSpecificFailureWinsWhenAMessageMentionsBoth() {
        assertEquals(
            "This video file cannot be played.",
            PlaybackErrorMessage.forError(null, "codec failure during network read"),
        )
    }

    @Test
    fun nothingRecognisedIsSaidPlainly() {
        assertEquals("Unknown error", PlaybackErrorMessage.forError(null, null))
        assertEquals("Unknown error", PlaybackErrorMessage.forError("99999", "something new"))
    }
}
