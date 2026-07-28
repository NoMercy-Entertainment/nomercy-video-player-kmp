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
// These defaults are DEFAULT_ON from responsive.ts, which the browser export
// settled: the web enables play, mute, volume, fullscreen, settings, next,
// previous and the two chapter jumps, and leaves the other ten off until a
// consumer asks for them.
//
// They used to be transport-only, on the reasoning that a bar shipping every
// button enabled would put picture-in-picture, theater mode and a quality menu
// in front of consumers whose builds cannot support them. That reads well and
// it produced a chrome with five of the web's controls, because nobody ever
// turned the rest on — and the web answers the same worry differently, by
// hiding a control the content or the build cannot support. The state gates
// here already do that: the chapter buttons need chapters, the quality menu
// needs a ladder, the audio menu needs more than one track.
//
// So the flags stay, because a build genuinely without picture-in-picture needs
// a way to say so, and three defaults changed: chapters, fullscreen and
// settings are now on. This alters what an existing consumer sees, which is why
// it was held until the render check could name the set rather than guess it.
//
// `time` has no counterpart in the web's list because the elapsed and remaining
// labels are not buttons there — they are part of the reserved row width. It is
// a flag here because this chrome draws them, and it is on for the same reason
// the web always shows them.
public data class ChromeButtons(
    val playPause: Boolean = true,
    val previousNext: Boolean = true,
    val volume: Boolean = true,
    val time: Boolean = true,
    val chapters: Boolean = true,
    val fullscreen: Boolean = true,
    val settings: Boolean = true,

    // Off until asked for, exactly as on the web.
    val seekBack: Boolean = false,
    val seekForward: Boolean = false,
    val subtitles: Boolean = false,
    val audio: Boolean = false,
    val quality: Boolean = false,
    val speed: Boolean = false,
    val aspectRatio: Boolean = false,
    val playlist: Boolean = false,
    val theater: Boolean = false,
    val pictureInPicture: Boolean = false,
)
