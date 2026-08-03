// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.item

import tv.nomercy.player.core.media.Chapter
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.ports.SubtitleTrack

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
// Ten members, which is the ComplexInterface threshold, and the width is the
// point rather than a smell. Every one is a field a video chrome reads off the
// item a host already has — three image names because the reference accepts all
// three, subtitles and chapters because that is what the server sends and the
// player is what acts on it. Splitting it would give a host two interfaces to
// implement for one episode; a host implements only what it has, because every
// member past the first two has a default.
@Suppress("ComplexInterface")
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

    /**
     * What the item is about, drawn under its title on a playlist card.
     *
     * `.playlist-menu-button-overview` on the web, and TvChromeItem has carried
     * the field the whole time with nothing on either side of it: no item
     * declared one and the projection did not read one, so every card in the
     * pane drew a title over empty space.
     */
    public val description: String? get() = null

    /** Cover art, read when [image] is absent. */
    public val poster: String? get() = null

    /** Cover art, read when [image] and [poster] are both absent. */
    public val thumbnail: String? get() = null

    /**
     * Subtitle files that sit beside the item rather than inside it.
     *
     * The normal case in a NoMercy library: the film is one file and its
     * captions are a directory of `.vtt` next to it. An engine will never find
     * these — it only knows what is in the container it was handed — so an item
     * that does not declare them has no subtitles at all no matter how many are
     * on disk. Declared here for the same reason `chapters` is: it is what the
     * server sends, and the player is what has to act on it.
     *
     * Each entry needs a [tv.nomercy.player.core.ports.SubtitleTrack.url]; one
     * without is a menu row that cannot be played and is skipped.
     */
    public val subtitles: List<SubtitleTrack> get() = emptyList()

    /**
     * The item's own chapter markers.
     *
     * The comment on [subtitles] says these are declared "for the same reason
     * chapters is" and they were not declared at all — an item could not carry
     * chapters by any route, so a host had to call `chapters(list)` by hand for
     * every item it queued, and one that did not had a chapter bar with no
     * segments and a next-chapter button that moved nothing. The reference reads
     * them off the item.
     */
    public val chapters: List<Chapter> get() = emptyList()

    /**
     * A WebVTT file of chapter markers sitting beside the item, for a library
     * that stores them next to the captions instead of inlining them.
     *
     * Read only when [chapters] is empty: an item that states its markers
     * outright has already answered, and fetching a file to disagree with it
     * would make which one wins depend on which arrived first.
     */
    public val chapterFile: String? get() = null

    /** Series title, when the item is an episode. The lock screen's artist line. */
    public val show: String? get() = null

    /** Season number, 1-based. Rendered as the lock screen's album line. */
    public val season: Int? get() = null
}
