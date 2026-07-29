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

    // The top bar's cast affordance, and the only control gated on an option
    // rather than on state. Back and close appear when the host gives them
    // somewhere to go; cast cannot work that way, because pressing it only
    // surfaces the intent and the consumer opens its own device picker — so a
    // handler alone would put the button in front of viewers of every build
    // that has a picker but does not want one offered here.
    val cast: Boolean = false,

    // The auto-skip row in the settings list. Off by default because the web
    // has no such row and the seven there are gated against it; his player
    // draws it, which is what androidApp() turns on.
    val autoSkipChapters: Boolean = false,
) {

    public companion object {

        /**
         * The NoMercy Android client's own bar, as a configuration.
         *
         * The library draws the web's eighteen, because that is the contract a
         * consumer moving across expects and because picture-in-picture and
         * theater mode are framed-page ideas that a phone has no use for. What
         * the app actually shows is those eighteen with six turned off, which
         * is what these flags are for — the app is a configuration of the
         * faithful player, not a second player.
         *
         * Read off MobileBottomBar.kt and gated against it by
         * scripts/check-app-parity.py, so the two cannot drift apart quietly.
         */
        public fun androidApp(): ChromeButtons = ChromeButtons(
            playPause = true,
            previousNext = true,
            seekBack = true,
            seekForward = true,
            time = true,
            playlist = true,
            quality = true,
            audio = true,
            subtitles = true,
            settings = true,
            fullscreen = true,
            autoSkipChapters = true,

            // Off in his bar. Chapters and volume because a phone has hardware
            // keys and a scrubber; the other four because they are things a
            // framed page does and a full-screen phone player does not.
            chapters = false,
            volume = false,
            aspectRatio = false,
            theater = false,
            pictureInPicture = false,
            speed = false,
        )

        /**
         * The set each of the two players draws.
         *
         * A trailer gets subtitles and nothing else, which is what
         * `TrailerMobileUiPlugin` passes: no episodes, no quality, no audio.
         * Those rows would each open onto a list with one entry in it, because a
         * trailer is one file with one audio track — a press that costs a viewer
         * time and gives them no choice.
         *
         * Time stays. Somebody deciding whether to watch a two-minute trailer is
         * asking exactly how long it is.
         */
        public fun forKind(kind: VideoUiKind): ChromeButtons = when (kind) {
            VideoUiKind.Full -> ChromeButtons()
            VideoUiKind.Trailer -> ChromeButtons(
                playPause = true,
                previousNext = false,
                volume = true,
                time = true,
                chapters = false,
                fullscreen = true,
                settings = false,
                subtitles = true,
            )
        }
    }
}
