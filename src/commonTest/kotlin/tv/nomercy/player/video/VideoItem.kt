// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.ports.VideoBackend

// Named for what it is, now that it is the only thing here.
//
// The file was Fakes.kt and held the local FakeVideoBackend beside this. That
// copy went when the shipped one landed, leaving one declaration in a file
// named after two, which detekt says out loud and only on a full run — the
// commit that deleted the class ran the suite and not the lint.
internal data class VideoItem(
    override val id: String,
    override val url: String = "https://example.test/$id.m3u8",
    override val title: String? = null,
) : PlaylistItem

// An engine that reports tracks and answers on its own event stream.
//
// The tracks are the point: the video domain is track selection, and a fake
// that reported none would let every cycling test pass by doing nothing.
