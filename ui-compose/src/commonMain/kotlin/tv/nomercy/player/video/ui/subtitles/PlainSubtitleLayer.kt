// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.events.ALIGN_END
import tv.nomercy.player.core.events.ALIGN_START
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitleCue
import tv.nomercy.player.core.events.SubtitleStyle

/**
 * Plain-text cues — WebVTT, SRT — drawn as Compose text rather than rasterized.
 *
 * [AssSubtitleLayer]'s counterpart for the track that plugin will not draw:
 * `SubtitlePlugin.styled()` only accepts ASS/SSA, so a sidecar in any other
 * format reaches [CoreEvents.SubtitleCue] (the sidecar cue tracker already
 * emits it) with no consumer on the Android/desktop/iOS clients — every such
 * selection showed nothing (confirmed live, real TV, 2026-08-16).
 *
 * Geometry follows the web overlay's safezone (5% block, 3% inline) and its
 * cue-relative sizing, so the same preferences read the same on every client.
 * [SubtitleCue.line]/[SubtitleCue.position] (WebVTT's percentage placement)
 * are not read here yet — the common case (dialogue, bottom of frame) is what
 * this covers.
 */
@Composable
public fun PlainSubtitleLayer(
    player: ComposedPlayer,
    modifier: Modifier = Modifier,
    /** The viewer's caption styling. Alignment is per-cue and always wins. */
    subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** The host's own faces, by the name the style's font stack asks for. */
    fontResolver: (String) -> FontFamily? = { null },
) {
    var cues: List<SubtitleCue> by remember { mutableStateOf(emptyList()) }

    DisposableEffect(player) {
        val subscription = player.on(CoreEvents.SubtitleCue) { change -> cues = change.cues }
        onDispose { subscription.dispose() }
    }

    if (cues.isEmpty()) return

    BoxWithConstraints(modifier.fillMaxSize().testTag(PLAIN_SUBTITLE_TAG)) {
        val fill: TextStyle = subtitleStyle.toTextStyle(maxWidth.value, fontResolver)
        val outlineWidthPx: Float = with(LocalDensity.current) { fill.fontSize.toPx() } * SubtitleOutlineRatio
        val outline: TextStyle? = subtitleStyle.toOutlineStyle(fill, outlineWidthPx)
        val textBackground: Color = subtitleStyle.toBackgroundColor()
        val areaBackground: Color = subtitleStyle.toAreaColor()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = maxHeight * SAFE_BLOCK,
                    top = maxHeight * SAFE_BLOCK,
                    start = maxWidth * SAFE_INLINE,
                    end = maxWidth * SAFE_INLINE,
                ),
            verticalArrangement = Arrangement.Bottom,
        ) {
            cues.forEach { cue ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(areaBackground)
                        .padding(vertical = AREA_PADDING_DP.dp),
                    contentAlignment = cue.align.toAlignment(),
                ) {
                    Box(Modifier.background(textBackground)) {
                        val align: TextAlign = cue.align.toTextAlign()
                        val pad = Modifier.padding(horizontal = TEXT_PADDING_DP.dp)
                        outline?.let { stroke ->
                            BasicText(
                                text = cue.plainText,
                                style = stroke.copy(textAlign = align),
                                modifier = pad.clearAndSetSemantics { },
                            )
                        }
                        BasicText(
                            text = cue.plainText,
                            style = fill.copy(textAlign = align),
                            modifier = pad,
                        )
                    }
                }
            }
        }
    }
}

private fun String.toTextAlign(): TextAlign = when (this) {
    ALIGN_START -> TextAlign.Start
    ALIGN_END -> TextAlign.End
    else -> TextAlign.Center
}

private fun String.toAlignment(): Alignment = when (this) {
    ALIGN_START -> Alignment.CenterStart
    ALIGN_END -> Alignment.CenterEnd
    else -> Alignment.Center
}

/** Used when a host supplies no styling of its own. */
public val DefaultPlainSubtitleStyle: TextStyle = TextStyle(
    color = Color.White,
    fontSize = FONT_SIZE_SP.sp,
    shadow = Shadow(color = Color.Black, blurRadius = SHADOW_BLUR_PX),
)

internal const val PLAIN_SUBTITLE_TAG: String = "nm-plain-subtitles"

private const val FONT_SIZE_SP = 20
private const val AREA_PADDING_DP = 8
private const val TEXT_PADDING_DP = 8
private const val SAFE_BLOCK = 0.05f
private const val SAFE_INLINE = 0.03f
private const val SHADOW_BLUR_PX = 6f
