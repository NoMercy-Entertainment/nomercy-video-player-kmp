// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import tv.nomercy.player.video.subtitles.AssSurfaceFrame
import java.nio.IntBuffer

// Android takes the compositor's pixels as they are, so it keeps nothing
// between frames and ignores the changed region: an ARGB_8888 bitmap already
// stores premultiplied ints in this exact arrangement, and copying the buffer
// in is one memcpy rather than a pass over the surface.
//
// Software ARGB_8888, and that is not a detail: a hardware bitmap cannot be
// read back, so one allocated as such renders black with nothing reported —
// which looks exactly like a subtitle that failed to arrive.
//
// copyPixelsFromBuffer rather than createBitmap(int[]): createBitmap treats the
// ints it is given as straight alpha and converts them, which is a whole extra
// pass over the surface to undo what the compositor has already done.
internal actual class AssPictureSurface actual constructor() {

    actual fun bitmap(frame: AssSurfaceFrame, frameWidth: Int, frameHeight: Int): ImageBitmap {
        val bitmap: Bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(frame.pixels))
        return bitmap.asImageBitmap()
    }
}
