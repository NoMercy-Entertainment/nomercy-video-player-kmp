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
internal fun writeTestVideo(
    path: String,
    width: Int = FRAME_WIDTH,
    height: Int = FRAME_HEIGHT,
): File {
    val file = File(path)
    file.outputStream().buffered().use { out ->
        out.write("YUV4MPEG2 W$width H$height F10:1 Ip A1:1 C420jpeg\n".toByteArray())

        val chroma = ByteArray(width / 2 * (height / 2)) { CHROMA_NEUTRAL.toByte() }
        repeat(FRAME_COUNT) { frame ->
            out.write("FRAME\n".toByteArray())
            out.write(lumaPlane(frame, width, height))
            out.write(chroma)
            out.write(chroma)
        }
    }
    return file
}

// A square that moves, so a decoder stuck on one frame is as visible as one that
// decoded nothing.
private fun lumaPlane(frame: Int, width: Int, height: Int): ByteArray {
    val offset: Int = frame % (width / 2)
    return ByteArray(width * height) { index ->
        val x: Int = index % width
        val y: Int = index / width
        val inside: Boolean = x in offset until offset + width / 2 &&
            y in height / 4 until height * 3 / 4
        if (inside) LUMA_WHITE.toByte() else LUMA_BLACK.toByte()
    }
}
