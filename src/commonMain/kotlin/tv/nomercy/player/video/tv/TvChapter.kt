// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.tv

// A chapter of a film, as the bar draws it.
//
// Its own type rather than the player's, because a bar needs a start and a name
// while the player's carries everything a chapter has. Keeping them apart is
// what lets the bar be drawn from a fixture rather than from a loaded film.
public data class TvChapter(
    val startSeconds: Double,
    val title: String? = null,
)

// What the transport row is showing.
//
// One value rather than four parameters. Four is where a caller passes the
// duration as the position and gets a bar that is always full, and the compiler
// cannot tell them apart because both are seconds.
public data class TvTransportState(
    val isPlaying: Boolean = false,
    val timeSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val chapters: List<TvChapter> = emptyList(),
)
