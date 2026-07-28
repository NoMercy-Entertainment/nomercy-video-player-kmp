// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

// A track a viewer can choose.
//
// Both lists are the same shape, so they are the same type. An audio track and a
// subtitle track differ in what they do and not in what a menu row needs to show
// for them.
public data class TvTrack(
    val id: String,
    val label: String,
    val language: String? = null,
    val isCurrent: Boolean = false,
)

// An episode in the list opened from the chrome.
public data class TvEpisode(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val isCurrent: Boolean = false,
)

// Everything the lists and the pre-screen draw from.
//
// Supplied by the host rather than fetched. A player library has no idea what is
// in a viewer's library and no business asking; what it owns is the fact that a
// television needs these lists reachable from a remote.
public data class TvChromeContent(
    val item: TvChromeItem? = null,
    val episodes: List<TvEpisode> = emptyList(),
    val audioTracks: List<TvTrack> = emptyList(),
    val subtitleTracks: List<TvTrack> = emptyList(),
)

// What the lists do when something is chosen.
//
// Separate from the transport callbacks because they are a different concern and
// a different consumer: a host that has no track lists implements the transport
// and leaves this empty.
public interface TvContentCallbacks {

    public fun selectEpisode(id: String)

    public fun selectAudioTrack(id: String)

    public fun selectSubtitleTrack(id: String)

    // Deliberately a separate action rather than a row in the subtitle list. It
    // reaches the network, and a viewer scrolling a list of what they already
    // have should not trip over something that does.
    public fun searchSubtitlesOnline()
}
