// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.item

import tv.nomercy.player.core.media.PlaylistItem

// A queue item with the two fields the video chrome reads off it.
//
// Core's item is an id, a url and a title, and that is right for core: an audio
// queue needs nothing more. A video card draws a runtime and a continue-watching
// bar, and both were reachable only through the host's own `itemOf` hook — so a
// host that handed the player a server payload and used the default got a
// playlist of bare titles with no runtime and no progress, while the fields to
// draw them sat on the item it had passed in.
//
// Implemented by a host's own item rather than constructed here. There is no
// data class: the point is that an episode from whatever server the host talks to
// can BE one of these without being copied into a shape this library invented.
public interface VideoPlaylistItem : PlaylistItem {
    /**
     * The item's length in seconds, when the host knows it before playback.
     *
     * Also the divisor the older progress shape needs: it carries a position and
     * no percentage, so without this the continue-watching bar has nothing to
     * compute against. See [normalizeWatchProgress].
     */
    public val durationSeconds: Double?

    /** Continue-watching state, in either wire shape. See [WatchProgress]. */
    public val progress: WatchProgress?
}
