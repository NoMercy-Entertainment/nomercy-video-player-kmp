// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import tv.nomercy.player.video.ui.chrome.ChromeTranslations
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import tv.nomercy.player.video.tv.TvChromeCallbacks
import tv.nomercy.player.video.tv.TvTransportState
import tv.nomercy.player.video.tv.formatTime

// The transport row along the bottom.
//
// Given state and callbacks rather than a store. What it drew before reached a
// playback store directly, which is why it could not be rendered anywhere except
// inside the application that owned one.
@Composable
public fun TvBottomBar(
    state: TvTransportState,
    callbacks: TvChromeCallbacks,
    strings: TvChromeStrings,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(BAR_PADDING).testTag(BOTTOM_BAR_TAG)) {
        ChapterProgressBar(
            timeSeconds = state.timeSeconds,
            durationSeconds = state.durationSeconds,
            chapters = state.chapters,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = ROW_GAP),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        ) {
            BasicText(text = formatTime(state.timeSeconds), style = TextStyle(color = Color.White))

            PlayerIconButton(
                icon = FluentIcons.Restart,
                description = strings.restart,
                onClick = callbacks::restart,
            )

            // The one control a viewer reaches for without looking, so it is the
            // one that takes focus when the bar appears.
            PlayerIconButton(
                icon = if (state.isPlaying) FluentIcons.Pause else FluentIcons.Play,
                description = if (state.isPlaying) strings.pause else strings.play,
                onClick = callbacks::togglePlay,
                focusRequester = playFocusRequester,
            )

            PlayerIconButton(
                icon = FluentIcons.Next,
                description = strings.next,
                onClick = callbacks::next,
            )

            // Remaining rather than total. Somebody deciding whether to start
            // another episode is asking how much is left, not how long it was.
            BasicText(
                text = "-" + formatTime((state.durationSeconds - state.timeSeconds).coerceAtLeast(0.0)),
                style = TextStyle(color = Color.White),
            )
        }
    }
}

// Every string the chrome puts on screen, supplied by the host.
//
// A library that shipped English would be a library nobody outside English can
// use, and one that reached for a resource identifier would only work on
// Android.
// Built for a locale, so a viewer reads the string the browser renders.
//
// The defaults below stay English because a data class needs some, and because
// a host constructing one by hand should get something readable. Nobody should
// construct one by hand: `TvChromeStrings.of("nl")` reads the generated table,
// which is 79 locales of the web's own values.
//
// This class used to be the only translations the native chrome had — English,
// reworded on the way in. Both halves of that were divergence.
public fun tvChromeStrings(locale: String): TvChromeStrings {
    fun tip(name: String): String =
        ChromeTranslations.get(locale, "plugin.desktop-ui.tooltip.$name")

    return TvChromeStrings(
        play = tip("play"),
        pause = tip("play"),
        next = tip("next"),
        subtitles = tip("subtitles"),
        language = tip("audio"),
        close = tip("close"),
        previous = tip("previous"),
        seekBack = tip("seekBack"),
        seekForward = tip("seekForward"),
        chapterBack = tip("chapterPrev"),
        chapterForward = tip("chapterNext"),
        mute = tip("mute"),
        unmute = tip("mute"),
        aspectRatio = tip("aspectRatio"),
        theater = tip("theater"),
        pictureInPicture = tip("pip"),
        speed = tip("speed"),
        quality = tip("quality"),
        playlist = tip("playlist"),
        settings = tip("settings"),

        // The two top-bar labels, which are not tooltips on the web and so do
        // not live under the tooltip prefix. Reading them through `tip` would
        // have found nothing and quietly left the English defaults in place in
        // every locale.
        back = ChromeTranslations.get(locale, "plugin.desktop-ui.menu.back"),
        cast = ChromeTranslations.get(locale, "plugin.desktop-ui.button.cast"),

        // Two more the table carries that nothing was reading, so a Dutch
        // viewer's fullscreen button announced itself in English while the
        // Dutch word sat in the same file.
        fullscreen = tip("fullscreen"),
        episodes = ChromeTranslations.get(locale, "plugin.desktop-ui.menu.episodes"),
    )
}

public data class TvChromeStrings(
    val play: String = "Play",
    val pause: String = "Pause",
    val next: String = "Next",
    val restart: String = "Restart",
    val subtitles: String = "Subtitles",
    val episodes: String = "Episodes",
    val loading: String = "Loading",
    val resume: String = "Resume",
    val language: String = "Language",
    val searchSubtitles: String = "Search online",
    // Appended rather than placed beside the other transport labels, because a
    // data class is positional to anyone who constructed one that way and
    // inserting a field in the middle would silently move their arguments along.
    val close: String = "Close",

    // The web bar's labels, wording included, from
    // desktop-ui/i18n/en.ts. "Seek back 10 s" rather than "Rewind" because
    // that is what a viewer who has used the web player has read, and the
    // number in it is the step the button actually takes.
    //
    // Appended for the same reason close was: a data class is positional to
    // anyone who constructed one that way.
    val previous: String = "Previous",
    val seekBack: String = "Seek back 10 s",
    val seekForward: String = "Seek forward 10 s",
    val chapterBack: String = "Previous chapter",
    val chapterForward: String = "Next chapter",
    val mute: String = "Mute / Unmute",
    val unmute: String = "Mute / Unmute",
    val aspectRatio: String = "Aspect ratio",
    val theater: String = "Theater mode",
    val pictureInPicture: String = "Picture-in-picture",
    val speed: String = "Playback speed",
    val quality: String = "Quality",
    val playlist: String = "Playlist",
    val settings: String = "Settings",

    // Two labels for one button, because the glyph swaps and the label has to
    // swap with it. A fullscreen control still announcing "Fullscreen" while
    // the player is fullscreen tells a screen-reader user the opposite of what
    // pressing it does.
    val fullscreen: String = "Fullscreen",
    val exitFullscreen: String = "Exit fullscreen",

    // The top bar's two, appended for the same positional reason as the rest.
    val back: String = "Back",
    val cast: String = "Cast to device",
)

internal const val BOTTOM_BAR_TAG = "tv-bottom-bar"

private val BAR_PADDING = 24.dp
private val ROW_GAP = 12.dp
private val BUTTON_GAP = 8.dp
