// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import tv.nomercy.player.video.NMVideoPlayer

// The two bars, each resolved through its plugin slot before falling back to the
// chrome's own widget.
//
// Beside VideoChrome rather than inside it: that file is the assembly, and a
// reader following how the top bar is built should not have to scroll past the
// whole chrome to reach it.
@Composable
internal fun ChromeTop(scene: ChromeScene, host: ChromeHost) {
    ChromeSlotResolution(
        player = scene.player,
        slot = tv.nomercy.player.core.plugin.ChromeSlot.TopBar,
        hostOverride = host.slots.topBar,
        context = scene.slotContext,
        default = {
            ChromeTopBar(
                item = scene.state.item,
                strings = scene.strings,
                buttons = scene.buttons,
                exits = ChromeExits(host.onBack, host.onCast, host.onClose),
                hideTitle = scene.layout.hideTitle,
                pip = scene.state.pip,
            )
        },
    )
}

@Composable
internal fun ChromeBottom(scene: ChromeScene, host: ChromeHost, modifier: Modifier) {
    var scrub: Double? by remember { mutableStateOf(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val rowWidth: Dp = maxWidth

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(BOTTOM_STACK_TAG)
                .bottomScrim()
                // The gesture bar, for the same reason the top bar clears the
                // cutout: the picture uses the whole screen and the controls
                // must stay reachable. Nothing on a desktop.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = BOTTOM_STACK_PADDING),
            verticalArrangement = Arrangement.spacedBy(BOTTOM_STACK_GAP),
        ) {
            ChromeStrip(scene, host, rowWidth) { scrub = it }

            ChromeSlotResolution(
                player = scene.player,
                slot = tv.nomercy.player.core.plugin.ChromeSlot.Transport,
                hostOverride = host.slots.transport,
                context = scene.slotContext,
                default = {
                    TransportBar(
                        state = scene.state,
                        commands = scene.commands,
                        strings = scene.strings,
                        buttons = scene.buttons,
                        priority = scene.layout.priority,
                        portraitHidden = scene.layout.portraitHidden,
                        volumeSlider = scene.layout.volumeSlider,
                        buttonOrder = scene.layout.buttonOrder,
                    )
                },
            )
        }
    }
}

// The picture and the words on it, as one layer.
//
// Together rather than as two arguments because they are one thing to a viewer
// and because they have to stay in this order: the surface, then the cues, then
// everything else. A chrome that took them separately would let a caller supply
// a surface and no cues, which is the state this was in.
@Composable
internal fun BoxScope.ChromePicture(
    player: NMVideoPlayer,
    context: ChromeSlotContext,
    slots: ChromeSlots,
    surface: @Composable () -> Unit,
) {
    surface()
    SubtitleCueLayer(rememberCueBoxes(player), rememberSubtitleStyle(player))
    slots.styledSubtitles?.invoke(context.state, context.commands)
}
