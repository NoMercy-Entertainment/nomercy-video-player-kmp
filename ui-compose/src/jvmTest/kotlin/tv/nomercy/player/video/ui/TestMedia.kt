// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import java.io.File

internal const val FRAME_WIDTH = 64
internal const val FRAME_HEIGHT = 64
private const val FRAME_COUNT = 60
private const val LUMA_WHITE = 235
private const val LUMA_BLACK = 16
private const val CHROMA_NEUTRAL = 128

// Media the test writes for itself, so the gate needs no fixture and no encoder.
//
// Y4M is uncompressed frames behind a text header and libVLC demuxes it
// directly, which means a real engine decoding a real file without a megabyte of
// committed binary or an ffmpeg on the runner. Sixty frames of a white square on
// black: something that is unmistakably a picture rather than a fill, because a
// gate that accepted a uniform frame would pass on a black screen.
internal fun writeTestVideo(path: String): File {
    val file = File(path)
    file.outputStream().buffered().use { out ->
        out.write("YUV4MPEG2 W$FRAME_WIDTH H$FRAME_HEIGHT F10:1 Ip A1:1 C420jpeg\n".toByteArray())

        val chroma = ByteArray(FRAME_WIDTH / 2 * (FRAME_HEIGHT / 2)) { CHROMA_NEUTRAL.toByte() }
        repeat(FRAME_COUNT) { frame ->
            out.write("FRAME\n".toByteArray())
            out.write(lumaPlane(frame))
            out.write(chroma)
            out.write(chroma)
        }
    }
    return file
}

// A square that moves, so a decoder stuck on one frame is as visible as one that
// decoded nothing.
private fun lumaPlane(frame: Int): ByteArray {
    val offset: Int = frame % (FRAME_WIDTH / 2)
    return ByteArray(FRAME_WIDTH * FRAME_HEIGHT) { index ->
        val x: Int = index % FRAME_WIDTH
        val y: Int = index / FRAME_WIDTH
        val inside: Boolean = x in offset until offset + FRAME_WIDTH / 2 &&
            y in FRAME_HEIGHT / 4 until FRAME_HEIGHT * 3 / 4
        if (inside) LUMA_WHITE.toByte() else LUMA_BLACK.toByte()
    }
}
