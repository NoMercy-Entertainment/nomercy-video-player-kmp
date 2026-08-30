// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.foundation.Image
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.nomercy.player.video.tv.EpisodeLabels
import tv.nomercy.player.video.tv.TvChromeItem
import tv.nomercy.player.video.tv.episodeLabel
import tv.nomercy.player.video.tv.showTitle
import tv.nomercy.player.video.ui.tv.FluentIcons
import tv.nomercy.player.video.ui.tv.TvChromeStrings

// The name across the top, and the ways out.
//
// A port of desktop-ui/helpers/topBar.ts and the `.top-bar` rules beside it, and
// before that it was neither: a close cross with the title left-aligned against
// it. Both originals — the web bar and the MobileTopBar it was written to mirror
// — put the buttons on one side and the title on the other, right-aligned, under
// a gradient. Nothing about that was a matter of taste to get wrong.
//
// Every number here is the web's, written as the arithmetic that produced it
// rather than as a rounded result, because check-chrome-parity.py reads these
// constants and the stylesheet and compares them. A padding written as 48.dp is
// a number nobody can check; one written as the value the CSS states is.
//
// Which buttons appear follows the web exactly, and the rule is not the same for
// all three. Back and close are gated on having somewhere to go — the web hides
// them unless the consumer listens for the event, and a null callback here says
// the same thing. Cast is gated on the option instead, because a consumer can
// want the affordance without having wired the picker yet.
@Composable
public fun ChromeTopBar(
    item: TvChromeItem?,
    strings: TvChromeStrings,
    modifier: Modifier = Modifier,
    labels: EpisodeLabels = EpisodeLabels(),
    buttons: ChromeButtons = ChromeButtons(),
    exits: ChromeExits = ChromeExits(),
    /** `hideTitle` — a player under its own heading does not repeat it. */
    hideTitle: Boolean = false,
    /**
     * Whether the picture is in a floating window.
     *
     * `this.pipActive` on the web, where it hides both ways out: the chrome is not
     * what the viewer is looking at there, and a 40px circle over a thumbnail is
     * most of it.
     */
    pip: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // Before the padding, not after.
            //
            // A testTag placed after `.padding(...)` names the INNER box, so
            // this reported 51px where the browser's `#top-bar` is 115 — the
            // sixteen above and forty-eight below, and the gradient drawn over
            // both, all outside the thing being measured. The two title lines
            // inside it matched the reference to the pixel the whole time,
            // which is the signature of a box named at the wrong level rather
            // than a layout that is wrong.
            .testTag(CHROME_TOP_BAR_TAG)
            .background(Brush.verticalGradient(GRADIENT))
            // The cutout, and the status bar behind it.
            //
            // The picture goes THROUGH the notch — that is what fullscreen means
            // and the film should use every pixel. The controls must not: a close
            // button under a camera hole is a button nobody can press. This adds
            // the safe inset on a phone that has one and nothing at all on a
            // desktop, and it cancels itself when an ancestor has already consumed
            // the inset, so an embedded player halfway down a page does not get a
            // status bar's worth of empty space above its title.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(start = BAR_PADDING, top = BAR_PADDING, end = BAR_PADDING, bottom = BAR_BOTTOM_PADDING),
    ) {
        val width: Int = maxWidth.value.toInt()

        Row(
            modifier = Modifier.fillMaxWidth(),
            // Flex-start, not centred. The right column is two lines tall and the
            // left one is a single row of buttons; centring the two against each
            // other drops the buttons half a line down the picture.
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // `hidden = this.pipActive || !hasListeners(...)` — one expression on the web,
            // and one here: nulling a way out IS hiding it, because the button is
            // drawn only for a callback that exists.
            TopBarControls(
                strings,
                buttons,
                if (pip) exits.copy(onBack = null, onClose = null) else exits,
                trailing,
            )
            if (!hideTitle) TopBarTitle(item, strings, labels, width)
        }
    }
}

/**
 * How big the title is at this width, and whether the second line survives.
 *
 * The web shrinks `.title` twice on the way down — 1.05rem, then 0.9rem at
 * 480px, then 0.8rem at 360 — and hides `.show-info` outright below 360. This
 * drew 1.05rem at every width, so on a phone-sized pane the title was a fifth
 * larger than the browser's and the episode line was still there under it.
 *
 * The same three numbers the stylesheet states, in the same order, so
 * check-chrome-parity.py can read the media queries and this list and compare
 * them rather than trusting that somebody transcribed them.
 */
private fun titleRemFor(widthDp: Int): Float = when {
    widthDp <= TITLE_XS_MAX -> TITLE_REM_XS
    widthDp <= TITLE_SM_MAX -> TITLE_REM_SM
    else -> TITLE_REM
}

private fun showsEpisodeLine(widthDp: Int): Boolean = widthDp > TITLE_XS_MAX

/**
 * The three things the top bar can emit, together.
 *
 * One type rather than three parameters because the web treats them as one
 * decision — `applyStateVisibility()` decides which of these the viewer can
 * reach — and because a host wiring a player has all three answers at once or
 * none of them.
 *
 * Back and close are different exits. Back returns to wherever the viewer came
 * from and close dismisses the player where it stands, and a library offering
 * only the second leaves every consumer rebuilding the first by hand.
 */
public data class ChromeExits(
    val onBack: (() -> Unit)? = null,
    /**
     * Pressing this only surfaces the intent. The consumer opens its own device
     * picker, exactly as on the web, and a library that reached for a cast
     * session here would be choosing the consumer's protocol for it.
     */
    val onCast: (() -> Unit)? = null,
    val onClose: (() -> Unit)? = null,
)

// The left column: the ways out, and whatever the host adds beside them.
@Composable
private fun TopBarControls(
    strings: TvChromeStrings,
    buttons: ChromeButtons,
    exits: ChromeExits,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
    ) {
        // Registered or absent, which is the web's `hasListeners('back')`: a host
        // that wires nothing gets no button rather than a dead one.
        exits.onBack?.let { TopBarButton(FluentIcons.Back, strings.back, it, BACK_TAG) }

        if (buttons.cast) {
            exits.onCast?.let { TopBarButton(FluentIcons.Cast, strings.cast, it, CAST_TAG) }
        }

        exits.onClose?.let { TopBarButton(FluentIcons.Close, strings.close, it, CLOSE_TAG) }

        // Where a host puts what only it has: a chapter list, a share sheet.
        // Empty by default, because a slot the library filled would be one every
        // consumer has to undo.
        trailing()
    }
}

// The right column: what is playing, on two lines, right-aligned.
//
// Seventy-five per cent of the bar, which is `max-width: 75%` on the web and
// the same share in the app's own top bar. At 60 a show name reached the
// ellipsis after four words; the buttons still have the quarter they need.
@Composable
private fun RowScope.TopBarTitle(
    item: TvChromeItem?,
    strings: TvChromeStrings,
    labels: EpisodeLabels,
    widthDp: Int,
) {
    val episode: String = episodeLabel(item, labels)

    Column(
        modifier = Modifier.fillMaxWidth(RIGHT_COLUMN_SHARE),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(TEXT_GAP),
    ) {
        BasicText(
            text = showTitle(item, strings.loading),
            style = TITLE_STYLE.copy(fontSize = (titleRemFor(widthDp) * REM).sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Named, because the reference addresses it as `#title` and only the
            // second line had a tag. A geometry comparison pairing them by what
            // was available put the web's first line against the native's
            // second and reported both as misplaced.
            modifier = Modifier.testTag(CHROME_TITLE_TAG),
        )

        // Absent rather than blank on a film, whose name is already on the line
        // above. The web sets `hidden` on the same condition; an empty second
        // line is a gap a viewer reads as something that failed to load.
        //
        // And gone entirely on the narrowest panes, where the web sets
        // `display: none` on it: below 360 there is no room for two lines
        // beside the buttons without the title truncating to nothing.
        if (episode.isNotEmpty() && showsEpisodeLine(widthDp)) {
            BasicText(
                text = episode,
                style = SHOW_INFO_STYLE,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = SHOW_INFO_BOTTOM).testTag(EPISODE_TAG),
            )
        }
    }
}

// A round translucent button, which is what the web's `.back-btn, .cast-btn,
// .close-btn` rule draws.
//
// Not PlayerIconButton: that one is the television's, forty-eight units across
// and filling white when focus lands on it, because a remote has no pointer and
// focus is the only cue. These are pressed with a finger or a mouse.
@Composable
private fun TopBarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tag: String,
) {
    val interaction: MutableInteractionSource = remember { MutableInteractionSource() }
    val hovered: Boolean by interaction.collectIsHoveredAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // requiredSize for the same reason the transport buttons use it:
            // a coerced height turns the rest fill these three carry into an
            // oval, and these are the ones drawn filled at all times.
            .requiredSize(BUTTON_SIZE)
            .background(if (hovered) BUTTON_HOVER_FILL else BUTTON_REST_FILL, CircleShape)
            .hoverable(interaction)
            // Its own indication rather than the platform's, because the web
            // states one: these three are the only buttons in the chrome with a
            // fill at rest, and a ripple over it is a second answer to the same
            // question.
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

internal const val CHROME_TOP_BAR_TAG = "nm-chrome-top-bar"

/** The show title, the top bar's first line. The web's `#title`. */
internal const val CHROME_TITLE_TAG = "nm-chrome-title"
internal const val BACK_TAG = "nm-chrome-back"
internal const val CAST_TAG = "nm-chrome-cast"
internal const val CLOSE_TAG = "nm-chrome-close"
internal const val EPISODE_TAG = "nm-chrome-episode"

// `padding: 16px 16px 48px 16px`. The bottom one is three times the others
// because it is not spacing — it is the length the gradient has to fade over,
// and a bar padded evenly puts a hard edge across the picture.
private val BAR_PADDING = 16.dp
private val BAR_BOTTOM_PADDING = 48.dp

// `margin-right: 8px` on each button, and `gap: 0.5rem` down the right column.
private val BUTTON_GAP = 8.dp
private val TEXT_GAP = 8.dp

// `width: 40px; height: 40px` with the glyph rendered at svgFromIcon's default 22.
private val BUTTON_SIZE = 40.dp
private val ICON_SIZE = 22.dp

// `background: rgba(0, 0, 0, 0.35)`.
private const val BUTTON_BACKGROUND_ALPHA = 0.35f

// `background: rgba(0, 0, 0, 0.35)` at rest, and on hover
// `color-mix(in srgb, #fff 10%, rgba(0, 0, 0, 0.35))` — which resolves to an
// alpha of 0.1 + 0.9x0.35 and a channel of 0.1x255 over that, so a lighter grey
// AND a less transparent one. Written as the resolved values because Compose has
// no colour-mix and the arithmetic is the part that would drift.
private val BUTTON_REST_FILL: Color = Color.Black.copy(alpha = BUTTON_BACKGROUND_ALPHA)
private val BUTTON_HOVER_FILL: Color = Color(red = 61, green = 61, blue = 61, alpha = 106)

// `max-width: 60%`.
private const val RIGHT_COLUMN_SHARE = 0.75f

// `linear-gradient(to bottom, rgba(0,0,0,0.85), rgba(0,0,0,0.4), rgba(0,0,0,0))`.
// Three stops, not two: the middle one is what keeps the fade from looking like
// a grey band laid over the picture.
private val GRADIENT = listOf(
    Color.Black.copy(alpha = 0.85f),
    Color.Black.copy(alpha = 0.40f),
    Color.Transparent,
)

// Sizes are rem on the web and the browser's root is sixteen pixels, so the
// arithmetic is written out rather than the answer: 1.05rem is 16.8, not 17.
private const val REM = 16f

// The three widths the stylesheet names, and the sizes at each. Written as the
// rem the CSS states rather than as sp, for the same reason every other number
// here is: the gate reads the media queries and these constants and diffs them.
private const val TITLE_REM = 1.05f
private const val TITLE_REM_SM = 0.9f
private const val TITLE_REM_XS = 0.8f

// `@container (max-width: 480px)` and `@container (max-width: 360px)`.
private const val TITLE_SM_MAX = 480
private const val TITLE_XS_MAX = 360

private val TITLE_STYLE = TextStyle(
    color = Color.White,
    fontSize = (TITLE_REM * REM).sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.End,
    // `text-shadow: 0 1px 4px rgba(0, 0, 0, 0.8)`. A white title over a bright
    // frame is unreadable without it, which is exactly when it matters.
    shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(0f, 1f), blurRadius = 4f),
)

private val SHOW_INFO_STYLE = TextStyle(
    color = Color.White.copy(alpha = 0.75f),
    fontSize = (0.88f * REM).sp,
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.End,
)

// `margin-bottom: 2px`.
private val SHOW_INFO_BOTTOM = 2.dp
