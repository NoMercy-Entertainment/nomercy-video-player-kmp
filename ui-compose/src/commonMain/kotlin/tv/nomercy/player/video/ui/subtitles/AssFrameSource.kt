// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

/** A video frame the decoder has announced: its own media time and when it reaches the screen. */
public class AssFrameTick(
    public val timeMs: Long,
    public val releaseNs: Long,
)

/**
 * The decoder's frame announcements, which drive the ASS layer instead of a timer.
 *
 * The layer renders for [AssFrameTick.timeMs] as soon as a frame is announced and shows the
 * picture when that frame is released, so the sign is drawn for the frame it appears with.
 */
public interface AssFrameSource {

    /** Suspends until a frame is announced and answers the newest one not yet taken. */
    public suspend fun next(): AssFrameTick

    /** Suspends until [tick] is on screen; returns at once when it already is. */
    public suspend fun awaitRelease(tick: AssFrameTick)
}
