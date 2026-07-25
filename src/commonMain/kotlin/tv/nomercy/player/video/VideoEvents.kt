// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import tv.nomercy.player.core.events.EventKey

// The events only a video player has, on top of everything CoreEvents already
// carries. Nineteen keys, each name identical to its web VideoEventMap entry.
//
// A listener for a core event uses CoreEvents even on a video player: there is
// one bus and one set of names, and the split is about which library owns the
// declaration, not about which bus the event travels on.
public object VideoEvents {
    public val AspectRatio: EventKey<AspectRatioChange> = EventKey("aspectRatio")
    public val AudioTracks: EventKey<AudioTracksChange> = EventKey("audioTracks")

    // The remote's back button, so a chrome can close a panel instead of the
    // platform closing the player.
    public val Back: EventKey<Unit> = EventKey("back")

    public val CanPlay: EventKey<Unit> = EventKey("canplay")
    public val Cast: EventKey<Unit> = EventKey("cast")
    public val Close: EventKey<Unit> = EventKey("close")
    public val DisplayMessage: EventKey<tv.nomercy.player.video.DisplayMessage> = EventKey("display-message")
    public val Fullscreen: EventKey<ViewModeChange> = EventKey("fullscreen")
    public val Levels: EventKey<LevelsChange> = EventKey("levels")
    public val Pip: EventKey<ViewModeChange> = EventKey("pip")
    public val QualityRequested: EventKey<QualityRequest> = EventKey("quality:requested")
    public val RemoveMessage: EventKey<Unit> = EventKey("remove-message")
    public val SegmentBoundary: EventKey<tv.nomercy.player.video.SegmentBoundary> = EventKey("segmentBoundary")
    public val Stalled: EventKey<Unit> = EventKey("stalled")
    public val SubtitleSizeDown: EventKey<Unit> = EventKey("subtitle-size-down")
    public val SubtitleSizeUp: EventKey<Unit> = EventKey("subtitle-size-up")
    public val Theater: EventKey<ViewModeChange> = EventKey("theater")
    public val VideoRect: EventKey<VideoRectChange> = EventKey("videoRect")
    public val Waiting: EventKey<Unit> = EventKey("waiting")

    // Every key, for the conformance gate that checks this registry against the
    // contract's video map.
    public val all: List<EventKey<*>> = listOf(
        AspectRatio,
        AudioTracks,
        Back,
        CanPlay,
        Cast,
        Close,
        DisplayMessage,
        Fullscreen,
        Levels,
        Pip,
        QualityRequested,
        RemoveMessage,
        SegmentBoundary,
        Stalled,
        SubtitleSizeDown,
        SubtitleSizeUp,
        Theater,
        VideoRect,
        Waiting,
    )
}
