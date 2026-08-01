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

    /**
     * Cover art for the item, read first of the three image fields.
     *
     * Three names for one picture because the web item accepts all three and
     * reads them in this order — a host whose backend calls it a poster and a
     * host whose backend calls it a thumbnail both work without a mapping step.
     * This is what a lock screen and a notification draw.
     */
    public val image: String? get() = null

    /** Cover art, read when [image] is absent. */
    public val poster: String? get() = null

    /** Cover art, read when [image] and [poster] are both absent. */
    public val thumbnail: String? get() = null

    /** Series title, when the item is an episode. The lock screen's artist line. */
    public val show: String? get() = null

    /** Season number, 1-based. Rendered as the lock screen's album line. */
    public val season: Int? get() = null
}
