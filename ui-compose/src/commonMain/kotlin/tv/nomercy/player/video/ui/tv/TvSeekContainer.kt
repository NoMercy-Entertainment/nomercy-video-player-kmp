// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.core.format.formatSeconds
import tv.nomercy.player.video.thumbnails.frameIndexAt
import tv.nomercy.player.video.thumbnails.spriteFrameAspect
import tv.nomercy.player.video.tv.TvChromeCallbacks
import tv.nomercy.player.video.tv.TvTransportState
import tv.nomercy.player.video.ui.chrome.rememberChromeStrings
import tv.nomercy.player.video.ui.thumbnails.PreviewSprite
import tv.nomercy.player.video.ui.thumbnails.SpriteFramePreview

// Scrubbing with a remote.
//
// A remote has no scrub bar, only directions, so the position moves in steps and
// the picture underneath does not move at all until the viewer commits. That is
// the whole design: what they are looking at while they hunt is a preview, and
// the film stays where it was until they choose.
//
// What they are looking at is the SHEET, laid out as a strip. His TVSeekContainer
// steps an index across `previewSprite.frames` and commits
// `frames[selectedIndex].timeSeconds` — one press is one thumbnail, and the
// viewer picks a picture out of a row of them rather than aiming a clock at a
// scene they cannot see. A fixed step in seconds is a different interaction
// wearing the same diff: the strip stops lining up with the presses, and on a
// sheet generated every ten seconds two presses can land on one thumbnail.
//
// Without a sheet there is no strip, so the arrows fall back to a step in
// seconds. His container renders nothing at all in that case and handles no keys
// with it — a host that supplied no thumbnails could not scrub on a television.
@Composable
public fun TvSeekContainer(
    state: TvTransportState,
    callbacks: TvChromeCallbacks,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    sprite: PreviewSprite? = null,
    strings: TvChromeStrings = rememberChromeStrings(),
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val strip: SeekStrip = remember(sprite, state.durationSeconds) {
        SeekStrip(sprite?.frames.orEmpty(), state.durationSeconds)
    }
    var cursor: SeekCursor by remember(strip, state.timeSeconds) {
        mutableStateOf(strip.cursorAt(state.timeSeconds))
    }

    // Focus has to land here the moment it appears, or the arrows keep going to
    // whatever had it before and the strip somebody is looking at does not move.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BoxWithConstraints(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .fillMaxWidth()
            .testTag(SEEK_TAG)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val pressed: SeekPress = pressFor(event.key, event.type, cursor, strip)
                cursor = pressed.cursor
                dispatch(pressed, callbacks, onCommit, onCancel)
                pressed.outcome != SeekOutcome.IGNORED
            }
            .semantics { contentDescription = formatSeconds(cursor.seconds) },
    ) {
        // Centred by padding the row by half the leftover width, as he does, so
        // the selected tile sits under the ring wherever it is in the sheet —
        // including the first and the last, which an offset could not reach.
        sprite?.let { sheet ->
            SeekFilmstrip(
                sprite = sheet,
                selected = cursor.index,
                label = strings.seekPreview,
                centrePadding = (maxWidth - FRAME_WIDTH) / 2,
            )
        }
    }
}

// What the press did, applied.
//
// Split out so the widget above stays a layout: the transition table decides,
// this carries the decision out, and neither is reading the other's job.
private fun dispatch(
    pressed: SeekPress,
    callbacks: TvChromeCallbacks,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
) {
    when (pressed.outcome) {
        SeekOutcome.MOVED -> callbacks.overrideTime(pressed.cursor.seconds.toFloat())
        SeekOutcome.COMMITTED -> onCommit(pressed.cursor.seconds.toFloat())
        SeekOutcome.CANCELLED -> onCancel()
        SeekOutcome.IGNORED -> Unit
    }
}

// The sheet as a row of thumbnails, with the selected one under a ring.
//
// The row does not scroll under a finger — `userScrollEnabled = false` — because
// there is no finger. It is moved by the arrows, which is what makes the strip
// and the presses one thing.
@Composable
private fun SeekFilmstrip(
    sprite: PreviewSprite,
    selected: Int,
    label: String,
    centrePadding: Dp,
) {
    val listState: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val aspect: Float = spriteFrameAspect(sprite.frames)

    LaunchedEffect(selected) { listState.animateScrollToItem(selected.coerceAtLeast(0)) }

    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(FRAME_WIDTH / aspect)
                .zIndex(STRIP_Z),
            contentPadding = PaddingValues(horizontal = centrePadding),
            horizontalArrangement = Arrangement.spacedBy(FRAME_GAP),
            userScrollEnabled = false,
        ) {
            itemsIndexed(sprite.frames) { index, frame ->
                SeekFrame(sprite = sprite, frame = frame, selected = index == selected, label = label)
            }
        }

        SeekRing(aspect)
    }
}

// One thumbnail of the strip.
//
// The pixels come from SpriteFramePreview, which is the frame drawing the mobile
// chrome already does — the offset arithmetic, the declared aspect and the blank
// pass while a band is still being read all live there. A second copy here is
// how one of the two ends up corrected.
@Composable
private fun SeekFrame(
    sprite: PreviewSprite,
    frame: SpriteCue,
    selected: Boolean,
    label: String,
) {
    Box(
        modifier = Modifier
            .width(FRAME_WIDTH)
            .scale(if (selected) SELECTED_SCALE else UNSELECTED_SCALE)
            .clip(RoundedCornerShape(FRAME_RADIUS))
            // Every tile says which moment it is, so a screen reader walking the
            // strip reads times rather than "image, image, image".
            .semantics { contentDescription = "$label ${formatSeconds(frame.start)}" },
    ) {
        SpriteFramePreview(sprite = sprite, seconds = frame.start, width = FRAME_WIDTH)
    }
}

// The white outline over the middle of the strip, which is what "selected" means
// here: the tile does not move to the viewer, the viewer moves the strip under a
// mark that never moves.
//
// A bare Canvas rather than the transparent Surface his file wraps it in. Same
// pixels, and this module draws on foundation alone — pulling Material in would
// hand every consumer of the library a design system they did not ask for.
@Composable
private fun SeekRing(aspect: Float) {
    Canvas(
        modifier = Modifier
            .width(FRAME_WIDTH)
            .height(FRAME_WIDTH / aspect)
            .scale(SELECTED_SCALE)
            .zIndex(RING_Z),
    ) {
        val stroke: Float = RING_STROKE.toPx()

        drawRoundRect(
            color = RING_COLOUR,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(RING_CORNER_PX, RING_CORNER_PX),
            style = Stroke(width = stroke),
        )
    }
}

// The transition table on its own, so the widget stays a layout and this stays
// readable. It is also the part worth reasoning about: where the cursor lands,
// and whether the film moves at all.
internal fun pressFor(key: Key, type: KeyEventType, cursor: SeekCursor, strip: SeekStrip): SeekPress {
    if (type != KeyEventType.KeyDown) return SeekPress(cursor, SeekOutcome.IGNORED)

    return when (key) {
        Key.DirectionLeft -> SeekPress(strip.stepped(cursor, -1), SeekOutcome.MOVED)

        Key.DirectionRight -> SeekPress(strip.stepped(cursor, 1), SeekOutcome.MOVED)

        Key.DirectionCenter, Key.Enter -> SeekPress(cursor, SeekOutcome.COMMITTED)

        // Leaving without moving the film. The preview is abandoned and the
        // display goes back to where playback actually is.
        Key.Back -> SeekPress(cursor, SeekOutcome.CANCELLED)

        else -> SeekPress(cursor, SeekOutcome.IGNORED)
    }
}

// The strip the arrows walk, and the only thing that knows whether there is one.
//
// Both callers of `stepped` are the same press; which arithmetic it gets is a
// property of the sheet, not of the key. Deciding that here rather than in the
// transition table is what keeps the table readable as the six presses it is.
internal class SeekStrip(
    internal val frames: List<SpriteCue>,
    internal val durationSeconds: Double,
) {

    internal fun stepped(cursor: SeekCursor, by: Int): SeekCursor =
        if (frames.isEmpty()) {
            steppedTime(cursor.seconds, by, durationSeconds)
        } else {
            steppedFrame(frames, cursor.index, by)
        }

    // Where the film actually is, as a place in the strip. frameIndexAt is the
    // shared lookup the browser and both chromes agree on, including its
    // hold-the-last-frame tail — which is the same answer his `indexOfLast { … }
    // .coerceIn(0, lastIndex)` gives on a contiguous sheet.
    internal fun cursorAt(seconds: Double): SeekCursor =
        SeekCursor(frameIndexAt(frames, seconds) ?: NO_FRAME, seconds)
}

// Where the viewer is looking while they hunt: which frame, and the second it
// stands for. Both, because the strip is drawn from the index and the commit is
// made from the seconds, and deriving either from the other is where they drift.
internal data class SeekCursor(val index: Int, val seconds: Double)

internal data class SeekPress(val cursor: SeekCursor, val outcome: SeekOutcome)

internal enum class SeekOutcome { MOVED, COMMITTED, CANCELLED, IGNORED }

// One press, one thumbnail. `selectedIndex - 1` and `selectedIndex + 1` coerced
// into the sheet, and the second is read off the frame itself rather than
// computed from the step — a sheet's interval is whatever generated it, and a
// step multiplied by an assumed interval walks away from the picture on screen.
internal fun steppedFrame(frames: List<SpriteCue>, index: Int, by: Int): SeekCursor {
    val landed: Int = (index + by).coerceIn(0, frames.lastIndex)

    return SeekCursor(landed, frames[landed].start)
}

// No sheet, no strip. A remote still has to be able to scrub, so what is left is
// a fixed step in seconds — and the index goes nowhere, because there is nothing
// to index.
internal fun steppedTime(seconds: Double, by: Int, duration: Double): SeekCursor =
    SeekCursor(NO_FRAME, (seconds + by * STEP_SECONDS).coerceIn(0.0, duration))

internal const val SEEK_TAG = "tv-seek-container"

// No frame under the cursor, which is a sheet that has not arrived rather than a
// position off the end of one.
private const val NO_FRAME = -1

// Ten seconds a press, for the sheetless fallback only. Short enough to land on
// a scene, and the coloured buttons on the same remote already cover the long
// jumps.
private const val STEP_SECONDS = 10.0

// `itemWidthDp = 280.dp`, from TvUiPlugin.kt where his strip is mounted. The
// height is not a second number: it comes from what the sheet declares a frame
// measures, which is the same source SpriteFramePreview sizes each tile from, so
// the row and its contents cannot disagree.
private val FRAME_WIDTH: Dp = 280.dp

// `Arrangement.spacedBy(16.dp)` and `RoundedCornerShape(8.dp)`.
private val FRAME_GAP: Dp = 16.dp
private val FRAME_RADIUS: Dp = 8.dp

// `.scale(if (isSelected) 1.1f else 1.0f)`. The selected tile stands proud of
// its neighbours, which is what makes the strip readable from a sofa.
private const val SELECTED_SCALE = 1.1f
private const val UNSELECTED_SCALE = 1.0f

// `val stroke = 4.dp.toPx()`, `Color.White.copy(alpha = 0.9f)`, and
// `CornerRadius(12f, 12f)` — pixels in his file, not dp, so pixels here.
private val RING_STROKE: Dp = 4.dp
private const val RING_ALPHA = 0.9f
private const val RING_CORNER_PX = 12f
private val RING_COLOUR = Color.White.copy(alpha = RING_ALPHA)

// `.zIndex(5f)` on the row and `.zIndex(10f)` on the outline over it.
private const val STRIP_Z = 5f
private const val RING_Z = 10f
