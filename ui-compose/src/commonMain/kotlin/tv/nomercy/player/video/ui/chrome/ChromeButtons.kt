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
// Transport is on and everything else is off, and that default is now the one
// thing in this file worth arguing about.
//
// The original reasoning: a bar shipping with every button enabled would put
// picture-in-picture, theater mode and a quality menu in front of every
// consumer whether their build supports them or not, and the ones that do not
// would be buttons that do nothing.
//
// It reads well and it produced a chrome with five of the web bar's twenty
// controls, because nobody ever turned the rest on. The web player shows this
// set by default and hides individual controls where the content or the build
// cannot support them — which the state gates here already do: the chapter
// buttons need chapters, the quality menu needs a ladder, the audio menu needs
// more than one track.
//
// So the flags stay, because a build genuinely without picture-in-picture needs
// a way to say so, and the DEFAULTS are what has to change to match the web.
// Left as they are for now and tracked in web-chrome-fidelity-spec.md: flipping
// them is a one-line change that alters what every existing consumer sees, and
// it lands with the side-by-side render check rather than ahead of it.
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
    val settings: Boolean = false,
)
