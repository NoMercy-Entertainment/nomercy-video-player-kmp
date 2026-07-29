// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle

// What is drawn over the picture rather than in a bar, and what all of it has in
// common: none of it fades with the chrome.
//
// A buffering line, whatever the player last said, the skip prompt and a failure
// each answer a question a viewer has RIGHT NOW, and four idle seconds does not
// make any of them less true. Keeping them together says that once instead of
// four times.

// The two lines that sit in the middle of the picture, both OUTSIDE the
// visibility gate.
//
// A film that is still buffering has to say so whether or not the controls are
// up: a viewer looking at a frozen frame with nothing on it cannot tell it from
// a crash. The same goes for whatever the player last said. Neither is part of
// the chrome that fades after four idle seconds, which is why they are drawn
// here rather than inside it.
@Composable
internal fun BoxScope.ChromeStatusText(scene: ChromeScene) {
    if (scene.state.buffering) {
        BasicText(
            text = scene.strings.loading,
            style = TextStyle(color = Color.White),
            modifier = Modifier.align(Alignment.Center).testTag(BUFFERING_TAG),
        )
    }

    scene.state.message?.let { text ->
        BasicText(
            text = text,
            style = TextStyle(color = Color.White),
            modifier = Modifier.align(Alignment.Center).testTag(MESSAGE_TAG),
        )
    }
}

// The layers that sit over the picture and are not the controls: the skip
// prompt and the failure. Split from ChromeLayers because that function is the
// assembly and this is what is layered on it, and together they are longer than
// one function is allowed to be.
@Composable
internal fun BoxScope.ChromeOverlays(scene: ChromeScene) {
        // The skip prompt, on the chapter the film is actually inside.
    //
    // Bottom-start for an opening and bottom-end for an ending, as his does,
    // so the two never appear in the same place and a viewer learns where to
    // look. Gated on a real chapter list: an item without chapters offers
    // nothing, which is most films.
    skipOffer(scene.state)?.takeIf { !scene.state.autoSkipChapters }?.let { offer ->
        SkipButton(
            kind = offer,
            label = if (offer == SkipKind.Intro) scene.strings.skipIntro else scene.strings.skipOutro,
            // To the end of the chapter being skipped, which is where the
            // next one starts. seekTo rather than a chapter-forward command,
            // because the boundary is already known here and asking the
            // player to find it again is a second answer that can differ.
            onSkip = { skipTargetOf(scene.state)?.let(scene.commands::seekTo) },
            modifier = Modifier.align(
                if (offer == SkipKind.Intro) Alignment.BottomStart else Alignment.BottomEnd,
            ),
        )
    }

    // Over the controls, not instead of them, which is what his overlay
    // does: a viewer reading why playback failed still needs the way out and
    // the playlist to pick something else. Outside the visibility gate for
    // the same reason the buffering line is — a failure that hides itself
    // after four seconds of no pointer movement is a failure nobody read.
    scene.state.error?.let { failure ->
        ChromeErrorOverlay(failure, Modifier.align(Alignment.Center))
    }
}
