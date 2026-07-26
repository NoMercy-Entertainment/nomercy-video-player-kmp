// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

// A renderer that records the order it was called in.
//
// Standing in for libass here is right rather than convenient: what the plugin
// is responsible for is fetching and sequencing, and libass has its own gates on
// hardware. What cannot be observed from a real renderer is the *order* — a
// track loaded before its fonts renders perfectly well in the wrong typeface.
class RecordingRenderer : AssRenderer {

    val calls: MutableList<String> = mutableListOf()
    val fonts: MutableMap<String, ByteArray> = mutableMapOf()
    var released: Boolean = false
        private set

    override fun addFont(name: String, data: ByteArray) {
        calls += "addFont:$name"
        fonts[name] = data
    }

    override fun loadTrack(assContent: String) {
        calls += "loadTrack"
    }

    override fun frameSize(width: Int, height: Int) {
        calls += "frameSize"
    }

    override fun render(timeMillis: Long): AssFrame? = null

    override fun release() {
        calls += "release"
        released = true
    }
}
