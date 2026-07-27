// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass.render

import android.graphics.Bitmap

// Frame-sized bitmaps, kept rather than reallocated.
//
// A 1080p ARGB_8888 frame is just over eight megabytes, and a subtitle track
// produces a new one every time a cue changes. Allocating those from the heap
// puts eight megabytes into the large-object space several times a second, and
// on a 256MB television that fragments the space until an allocation that should
// fit does not — an out-of-memory on a device with megabytes free.
//
// Nothing here ever calls Bitmap.recycle(). That is the rule this class exists
// to hold: the surface may still be holding the previous frame as the thing it
// is drawing, and freeing it underneath renders a crash inside the hardware
// renderer, reported against whatever happened to be on screen. A pooled bitmap
// is reused only after the caller hands it back, and a caller that hands one
// back too early gets a corrupted frame rather than a dead process.
public class BitmapPool(
    private val maxBytes: Int = MemoryTier.MEDIUM.bitmapPoolBytes,
) {

    private val available: MutableList<Bitmap> = mutableListOf()

    private var pooledBytes: Int = 0

    // An exact-size match or a fresh one. Reusing a larger bitmap and drawing
    // into part of it would leave the previous frame's pixels around the edges,
    // which reads as a subtitle with a torn border.
    public fun obtain(width: Int, height: Int): Bitmap {
        val index: Int = available.indexOfFirst { it.width == width && it.height == height }
        if (index < 0) return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val reused: Bitmap = available.removeAt(index)
        pooledBytes -= reused.byteCount
        reused.eraseColor(0)
        return reused
    }

    // Handing a frame back once the surface has stopped drawing it.
    //
    // A frame that does not fit the budget is dropped rather than recycled, and
    // dropping means letting go of the reference: the collector frees it when
    // nothing is drawing it, which is exactly the guarantee an explicit recycle
    // cannot give.
    public fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled || bitmap.config != Bitmap.Config.ARGB_8888) return

        // By identity, and only because a device test caught it. A caller that
        // releases the same frame on two paths — a normal-teardown path and an
        // error path — otherwise puts it in the pool twice, and the next two
        // cues are handed the same bitmap and draw both onto it.
        if (available.any { it === bitmap }) return

        if (pooledBytes + bitmap.byteCount > maxBytes) return

        available.add(bitmap)
        pooledBytes += bitmap.byteCount
    }

    public fun clear() {
        available.clear()
        pooledBytes = 0
    }

    public fun pooledByteCount(): Int = pooledBytes
}
