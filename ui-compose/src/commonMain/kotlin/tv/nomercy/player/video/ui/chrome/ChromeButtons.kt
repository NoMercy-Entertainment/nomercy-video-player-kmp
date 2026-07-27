// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

// Which controls a host wants on screen.
//
// Transport is on and everything else is off, which is the opposite of the
// obvious default and is deliberate. A bar that shipped with every button
// enabled would put picture-in-picture, theater mode and a quality menu in front
// of every consumer whether their build supports them or not, and the ones that
// do not would be buttons that do nothing.
//
// So a host turns on what it has. The cost is a line per feature; the cost the
// other way round is a control a viewer presses and nothing happens.
public data class ChromeButtons(
    val playPause: Boolean = true,
    val previousNext: Boolean = true,
    val volume: Boolean = true,
    val time: Boolean = true,

    val seekBack: Boolean = false,
    val seekForward: Boolean = false,
    val chapters: Boolean = false,
    val subtitles: Boolean = false,
    val audio: Boolean = false,
    val quality: Boolean = false,
    val speed: Boolean = false,
    val aspectRatio: Boolean = false,
    val playlist: Boolean = false,
    val theater: Boolean = false,
    val pictureInPicture: Boolean = false,
    val fullscreen: Boolean = false,
)
