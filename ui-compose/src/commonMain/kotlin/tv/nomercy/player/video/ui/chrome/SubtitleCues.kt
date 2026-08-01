// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.core.events.Subscription
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.subtitles.CueAlign
import tv.nomercy.player.video.subtitles.CueAnchor
import tv.nomercy.player.video.subtitles.CueBox
import tv.nomercy.player.video.subtitles.SAFE_AREA_INSET_BLOCK_PERCENT
import tv.nomercy.player.video.subtitles.SAFE_AREA_INSET_INLINE_PERCENT
import tv.nomercy.player.video.subtitles.SubtitleEdge
import tv.nomercy.player.video.subtitles.SubtitleOverlayPlugin
import tv.nomercy.player.video.subtitles.parseColorToHex
import tv.nomercy.player.video.subtitles.subtitleEdgeOf

// The cues, drawn.
//
// SubtitleOverlayPlugin has published laid-out boxes since it was ported and
// nothing read them, so a viewer who turned subtitles on saw the picture and no
// words — and every row of the subtitle settings menu wrote a preference into a
// style that reached no renderer. This is the other end of both.
//
// Positioned rather than laid out. The plugin already decided where each box
// goes as percentages of the safe area, so this turns percentages into Dp and
// nothing else; the collision handling, the anchor choice and the WebVTT box
// maths stay in CueLayout, where they can be checked without a screen.

/**
 * Every cue on screen, over the picture and under the controls.
 *
 * Outside the chrome's visibility gate, deliberately. Subtitles are not part of
 * the controls, and four idle seconds does not make a line of dialogue less
 * needed — the web's `.subtitle-overlay` sits at `z-index: 0` and never fades
 * with the bar.
 */
@Composable
internal fun BoxScope.SubtitleCueLayer(
    boxes: List<CueBox>,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    if (boxes.isEmpty()) return

    BoxWithConstraints(modifier = modifier.matchParentSize().testTag(SUBTITLE_LAYER_TAG)) {
        val insetX: Dp = maxWidth * (SAFE_AREA_INSET_INLINE_PERCENT / PERCENT).toFloat()
        val insetY: Dp = maxHeight * (SAFE_AREA_INSET_BLOCK_PERCENT / PERCENT).toFloat()
        val safe = SafeArea(
            insetX = insetX,
            insetY = insetY,
            width = maxWidth - insetX - insetX,
            height = maxHeight - insetY - insetY,
        )
        val paint = CuePaint(style, cueFontSize(maxWidth, style.fontSize))

        boxes.forEach { box -> CueArea(box, safe, paint) }
    }
}

// Where the cues may go, in Dp, so placing a box is one multiplication. The
// percentages are the plugin's; the insets are the stylesheet's.
private class SafeArea(
    val insetX: Dp,
    val insetY: Dp,
    val width: Dp,
    val height: Dp,
)

// Everything about how a cue looks that does not depend on which cue it is.
//
// Resolved once per pass rather than per box: nine style fields become four
// colours and a shadow, and doing that inside the loop repeats the work for
// every simultaneous cue.
private class CuePaint(style: SubtitleStyle, val fontSize: TextUnit) {
    val text: Color = subtitleColor(style.textColor, style.textOpacity)
    val textBackground: Color = subtitleColor(style.backgroundColor, style.backgroundOpacity)
    val area: Color = subtitleColor(style.areaColor, style.windowOpacity)
    val edge: SubtitleEdge = subtitleEdgeOf(style.edgeStyle)

    // The web's `getEdgeStyle` is handed the TEXT's opacity, not the
    // background's — an outline that stayed opaque behind a faded glyph would
    // read as a second font rather than as a shadow.
    val edgeColor: Color = subtitleColor(BLACK, style.textOpacity)
}

// One `.subtitle-area`, anchored by whichever edge the layout chose.
@Composable
private fun BoxScope.CueArea(box: CueBox, safe: SafeArea, paint: CuePaint) {
    val fromTop: Boolean = box.anchor == CueAnchor.Top

    // Top anchoring counts down from the safe area's top edge and bottom
    // anchoring counts up from its bottom. The plugin resolved which; the
    // percentage it hands back means the matching thing in each case.
    val offsetY: Dp = if (fromTop) {
        safe.insetY + safe.height * (box.linePercent / PERCENT).toFloat()
    } else {
        -(safe.insetY + safe.height * ((PERCENT - box.linePercent) / PERCENT).toFloat())
    }

    Box(
        modifier = Modifier
            .align(if (fromTop) Alignment.TopStart else Alignment.BottomStart)
            .offset(
                x = safe.insetX + safe.width * (box.leftPercent / PERCENT).toFloat(),
                y = offsetY,
            )
            .width(safe.width * (box.widthPercent / PERCENT).toFloat())
            .background(paint.area)
            .padding(vertical = AREA_PADDING),
        // `text-align` on the area, which places the inline-block inside it.
        contentAlignment = alignmentOf(box.align),
    ) {
        CueText(box, paint)
    }
}

// The `.subtitle-text` span, drawn as many times as the edge style stacks.
//
// `textShadow` is one shadow written seven times in the web's own table. A
// browser compounds them into an outline dense enough to read over snow; here
// each copy is a draw, and keeping one of the seven would leave a halo faint
// enough to look like a defect in the font rather than a setting.
@Composable
private fun CueText(box: CueBox, paint: CuePaint) {
    val density: Density = LocalDensity.current
    val style: TextStyle = cueTextStyle(box.align, paint, density)
    val span: Modifier = Modifier.background(paint.textBackground).padding(horizontal = TEXT_PADDING)

    Box {
        // The copies under the top one are the shadow and nothing else. Their
        // semantics are cleared or a screen reader announces the same line of
        // dialogue seven times, which is the default style — the port would have
        // made every caption in the player unusable to read aloud.
        repeat((paint.edge.layers - ONE_LAYER).coerceAtLeast(NO_LAYERS)) {
            BasicText(text = box.text, style = style, modifier = span.clearAndSetSemantics { })
        }

        BasicText(text = box.text, style = style, modifier = span)
    }
}

private fun alignmentOf(align: CueAlign): Alignment = when (align) {
    CueAlign.Start -> Alignment.CenterStart
    CueAlign.End -> Alignment.CenterEnd
    CueAlign.Center -> Alignment.Center
}

private fun cueTextStyle(align: CueAlign, paint: CuePaint, density: Density): TextStyle = TextStyle(
    color = paint.text,
    fontSize = paint.fontSize,
    fontWeight = FontWeight.Medium,
    lineHeight = paint.fontSize * LINE_HEIGHT,
    textAlign = when (align) {
        CueAlign.Start -> TextAlign.Start
        CueAlign.End -> TextAlign.End
        CueAlign.Center -> TextAlign.Center
    },
    shadow = shadowOf(paint, density),
)

private fun shadowOf(paint: CuePaint, density: Density): Shadow? {
    if (paint.edge.layers == NO_LAYERS) return null

    return with(density) {
        Shadow(
            color = paint.edgeColor,
            offset = Offset(paint.edge.offsetXPx.dp.toPx(), paint.edge.offsetYPx.dp.toPx()),
            blurRadius = paint.edge.blurPx.dp.toPx(),
        )
    }
}

/**
 * The size a cue is drawn at, from the picture's own width.
 *
 * `clamp(14px, calc(var(--subtitle-scale) * 2.5cqi), 56px)`. The middle term is
 * 2.5% of the player's inline size, which is what makes a subtitle the same
 * share of the picture on a phone and on a television rather than the same
 * number of pixels. The clamp is WCAG 1.4.4 — readable at the small end, sane at
 * the large one.
 */
internal fun cueFontSize(containerWidth: Dp, fontSizePercent: Int): TextUnit {
    val scaled: Float = containerWidth.value * BASE_SHARE * (fontSizePercent / PERCENT).toFloat()

    // Sp rather than Dp, and not scaled by the reader's text preference: the
    // browser has no such preference to honour, and a caption that grew with a
    // system setting would stop matching the frame it belongs to.
    return scaled.coerceIn(MIN_FONT_PX, MAX_FONT_PX).sp
}

/**
 * A style colour and its own opacity, as Compose wants it.
 *
 * Through the shared `parseColorToHex` rather than a second table here, so the
 * eight names the menu offers resolve to the values a browser draws.
 */
internal fun subtitleColor(value: String, opacityPercent: Int): Color =
    colorOfHex(parseColorToHex(value, opacityPercent / PERCENT))

// `#RRGGBBAA` to an ARGB int. The alpha byte is last in CSS and first here,
// which is the whole of the conversion and the whole of what can be wrong.
private fun colorOfHex(hex: String): Color {
    val digits: String = hex.removePrefix("#")
    if (digits.length != RGBA_DIGITS) return Color.Transparent

    val rgb: Long? = digits.take(RGB_DIGITS).toLongOrNull(HEX_RADIX)
    val alpha: Long? = digits.drop(RGB_DIGITS).toLongOrNull(HEX_RADIX)

    return if (rgb == null || alpha == null) {
        Color.Transparent
    } else {
        Color(((alpha shl ALPHA_SHIFT) or rgb).toInt())
    }
}

// The cues the subtitle overlay has laid out, if the player carries one.
//
// Read off the plugin rather than passed in by the host, because the plugin is
// where the geometry already is: a consumer that added it and a chrome that
// draws it should need no third piece of wiring between them. Nothing read this
// flow at all before, which is why a ported layout, a ported style and a
// settings menu that wrote to it all added up to a blank picture.
//
// Empty when no overlay is registered. That is a player whose subtitles are
// drawn elsewhere — the libass module paints its own — and not a fault.
@Composable
internal fun rememberCueBoxes(player: NMVideoPlayer): List<CueBox> {
    // By type rather than by the id string. `getPlugin` answers with the base
    // Plugin and would need a cast back; a registered subclass is still a
    // SubtitleOverlayPlugin and is still the one holding the boxes.
    //
    // Looked up each pass rather than remembered against the player. Adding a
    // plugin suspends, so an app does it from an effect — which runs AFTER the
    // first composition. A remembered lookup would cache the null it found
    // before the plugin existed and never draw a cue for the life of the screen.
    val overlay: SubtitleOverlayPlugin? =
        player.plugins.plugins().filterIsInstance<SubtitleOverlayPlugin>().firstOrNull()

    return if (overlay == null) emptyList() else overlay.boxes.collectAsState().value
}

// What the viewer chose, and every change to it while they are choosing.
//
// Seeded from the player rather than from the defaults so a style saved on
// another client is on screen at the first frame, then kept current from the
// event the settings menu's writes already emit — which is what makes a pane
// full of colours show its effect as it is being used rather than next time.
@Composable
internal fun rememberSubtitleStyle(player: NMVideoPlayer): SubtitleStyle {
    var style: SubtitleStyle by remember(player) { mutableStateOf(player.subtitleStyle()) }

    DisposableEffect(player) {
        val chosen: Subscription = player.on(CoreEvents.SubtitleStyle) { style = it }

        onDispose { chosen.dispose() }
    }

    return style
}

internal const val SUBTITLE_LAYER_TAG = "nm-subtitle-cues"

// `.subtitle-area { padding: 0.5rem 0 }` and `.subtitle-text { padding: 0 0.5rem }`.
private val AREA_PADDING: Dp = 8.dp
private val TEXT_PADDING: Dp = 8.dp

// `line-height: 1.2`.
private const val LINE_HEIGHT = 1.2f

// `2.5cqi` — 2.5% of the container's inline size.
private const val BASE_SHARE = 0.025f

// The clamp's two ends.
private const val MIN_FONT_PX = 14f
private const val MAX_FONT_PX = 56f

private const val BLACK = "black"
private const val PERCENT = 100.0
private const val NO_LAYERS = 0
private const val ONE_LAYER = 1
private const val HEX_RADIX = 16
private const val RGBA_DIGITS = 8
private const val RGB_DIGITS = 6
private const val ALPHA_SHIFT = 24
