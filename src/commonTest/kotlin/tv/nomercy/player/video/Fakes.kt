// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.BackendState
import tv.nomercy.player.core.ports.CanonicalBackendEvent
import tv.nomercy.player.core.ports.LoadOptions
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.core.ports.VideoBackend

internal data class VideoItem(
    override val id: String,
    override val url: String = "https://example.test/$id.m3u8",
    override val title: String? = null,
) : PlaylistItem

// An engine that reports tracks and answers on its own event stream.
//
// The tracks are the point: the video domain is track selection, and a fake
// that reported none would let every cycling test pass by doing nothing.
