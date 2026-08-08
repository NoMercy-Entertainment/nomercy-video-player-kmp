// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.ImageBitmap
import tv.nomercy.player.core.ports.VideoFrameSink

/**
 * The sink a software-decoded engine draws into, whichever bitmap this platform
 * has.
 *
 * Two implementations because two imaging libraries: Skia on the desktop and
 * android.graphics on the phone. Everything above this — the canvas, the
 * version counter that makes a reused bitmap repaint, the refusal of a short
 * frame — is one piece of code, because every one of those was got wrong once
 * already and a second copy is a second chance to get it wrong differently.
 *
 * The two also disagree about byte order, and that is why [VideoFrameSink] is
 * asked rather than assumed: Skia wants BGRA, ARGB_8888 is RGBA in memory.
 */
internal expect class ComposeFrameSink() : VideoFrameSink {

    /** The last complete frame, or null before one has arrived. */
    val frame: MutableState<ImageBitmap?>

    /**
     * Which frame is in [frame], so a redraw can be asked for without a
     * recomposition. A reused bitmap mutates pixels Compose is not watching, so
     * nothing repaints unless something it IS watching moves.
     */
    val version: MutableIntState

    /**
     * The canvas reporting the frame number it has painted.
     *
     * Declared for both platforms although only one has anything to do with it:
     * the desktop sink allocates a native picture per frame and this is the only
     * moment at which releasing an older one is provably safe, while Android
     * refills one bitmap and has nothing to release. A platform-specific call
     * site would mean the canvas knowing which sink it has, which is the coupling
     * this expect/actual pair exists to remove.
     */
    fun drawn(painted: Int)
}
