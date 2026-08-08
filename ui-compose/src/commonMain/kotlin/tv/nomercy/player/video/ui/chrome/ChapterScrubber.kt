// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.chrome

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.cues.SpriteCue
import tv.nomercy.player.core.format.formatSeconds
import tv.nomercy.player.core.media.Chapter
import tv.nomercy.player.video.thumbnails.frameAt

// Dragging a finger or a pointer along the film.
//
// The film does not move until the drag ends. What moves while a viewer is
// hunting is a preview, because seeking on every pixel of a drag is a seek storm
// the engine answers by stalling, and the picture they are trying to find never
// settles long enough to be recognised.
//
// The chapter marks come from the same bar the television uses, so a break is in
// the same place on both.
//
// A pointer merely RESTING on the strip counts as hunting too, and that was the
// half missing here. `wireSliderBar` binds `mouseover`, `mousemove` and
// `mouseleave` on the bar as well as the drag: the bubble appears under a still
// pointer, the hover fill follows it with no button held, and both clear when it
// leaves. This reacted to a drag and nothing else, so a viewer with a mouse had to
// commit to a drag before the player would tell them anything about where they
// were pointing — and a press that never travelled far enough to become a drag
// did nothing at all, where a click on the web seeks.
@Composable
public fun ChapterScrubber(
    state: ChromeState,
    commands: ChromeCommands,
    modifier: Modifier = Modifier,
    sprite: List<SpriteCue> = emptyList(),
    onScrubbing: (Boolean) -> Unit = {},
    onPreview: (SpriteCue?) -> Unit = {},
    /**
     * Where the drag is, in seconds, and null when it ends.
     *
     * Separate from [onPreview] because the two answer different questions and
     * one of them has an answer when the other does not: an item with no sprite
     * sheet still has a position, and the bubble still has a clock and a chapter
     * name to show. Reporting only the frame is why the chrome could not draw
     * one at all for items without thumbnails.
     *
     * Reported for a hover as well as a drag, which is what makes the bubble
     * appear under a resting pointer. Null means "nothing is being hunted" rather
     * than "the drag ended" — a pointer leaving the strip says it too.
     */
    onScrub: (Double?) -> Unit = {},
) {
    val duration: Double = state.durationSeconds
    val track: ScrubTrack = remember(duration) { ScrubTrack(duration) }

    val report: (Double?) -> Unit = { at ->
        onPreview(at?.let { frameAt(sprite, it) })
        onScrub(at)
    }

    // `.top-row:hover .slider-bar` and `.slider-bar.slider-scrubbing` are one
    // declaration with one answer, so the two reasons the bar is grown are one
    // flag here rather than two states that can disagree.
    val barHeight: Dp by animateDpAsState(
        targetValue = if (track.hunted == null) BAR_HEIGHT else BAR_HEIGHT_GROWN,
        animationSpec = tween(durationMillis = BAR_GROW_MS, easing = BAR_GROW_EASING),
    )

    Box(
        // Centred, so the target is symmetrical about the thing it is a target
        // FOR. Left to the default it was top-aligned: twenty-four units of
        // reach below the bar and none above it, which puts the bar 12 above
        // where the row it sits in expects it and opened a gap between the strip
        // and the controls the browser does not have. Nothing measured it,
        // because every control in the row was still the right size in the right
        // place — the ROW was in the wrong place.
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(SCRUBBER_TOUCH_HEIGHT)
            .testTag(SCRUBBER_TAG)
            // The whole strip is the target, not the few pixels the bar is
            // drawn in. A three-pixel drag target is one nobody hits with a
            // finger, and the drawn height is a design decision rather than a
            // reachability one.
            .onSizeChanged { track.width = it.width.toFloat().coerceAtLeast(1f) }
            .scrubPointers(duration, sprite, scrubGestures(track, commands, report, onScrubbing))
            .semantics { contentDescription = formatSeconds(track.drag ?: state.timeSeconds) },
    ) {
        ScrubberBar(state, track, barHeight)
    }
}

// Where the pointer is, and which way it got there.
//
// One holder rather than three separate `remember`s, so "hunted" — the union of a
// drag and a hover, which is what `.slider-scrubbing` and `:hover` are in a single
// declaration — has a name instead of being re-derived at every call site that
// needs it.
//
// The length lives here too, so the strip's scale is stated once rather than
// carried alongside the track through every handler that converts a position.
private class ScrubTrack(private val duration: Double) {
    var drag: Double? by mutableStateOf(null)
    var hover: Double? by mutableStateOf(null)
    var width: Float by mutableStateOf(1f)

    val hunted: Double? get() = drag ?: hover

    fun at(x: Float): Double = secondsAt(x, width, duration)
}

// The five things a pointer on the strip can say.
//
// A holder rather than five parameters on the modifier below, and its own type
// rather than a lambda each: the drag and the hover carry the same number and mean
// different things, and a pair of `(Float) -> Unit` parameters is two ways to pass
// the wrong one.
private class ScrubGestures(
    val begin: () -> Unit,
    val drag: (Float) -> Unit,
    val finish: (Boolean) -> Unit,
    /** A position while nothing is pressed, or null when the pointer leaves. */
    val hover: (Float?) -> Unit,
    val tap: (Float) -> Unit,
)

// What each of those does, wired to one place.
//
// Its own function because the composable above is at its length limit and this
// is the part that is a table rather than a layout.
private fun scrubGestures(
    track: ScrubTrack,
    commands: ChromeCommands,
    report: (Double?) -> Unit,
    onScrubbing: (Boolean) -> Unit,
): ScrubGestures = ScrubGestures(
    begin = { onScrubbing(true) },
    drag = { x ->
        track.drag = track.at(x)
        report(track.hunted)
    },
    finish = { commit ->
        // Only on a completed drag does the film move. A cancel leaves it alone.
        if (commit) track.drag?.let { commands.seekTo(it) }
        track.drag = null
        onScrubbing(false)
        // The pointer may still be resting on the strip, in which case the web's
        // `:hover` is still true and the bubble stays under it. Otherwise this is
        // the same clearing `mouseleave` does, and everything goes.
        report(track.hover)
    },
    hover = { x ->
        track.hover = x?.let { track.at(it) }
        // A drag outranks a hover. Both arrive together on a mouse, and a bubble
        // following the pointer rather than the drag would jump between them.
        if (track.drag == null) report(track.hover)
    },
    // `mousedown` and then a `mouseup` the document catches, with no travel in
    // between: `finalizeScrub` runs and seeks to where the pointer was let go.
    // detectDragGestures never fires for that, because a tap does not move far
    // enough to be a drag — so pressing the bar here did nothing whatsoever.
    tap = { x -> commands.seekTo(track.at(x)) },
)

// Three detectors on one strip, keyed on everything the handlers close over.
//
// The sprite is part of the key and was not, which is a stale-capture bug rather
// than a tidiness point: the sheet arrives after the item loads, and a detector
// launched before it does holds the empty list it was built with for the rest of
// the item.
private fun Modifier.scrubPointers(
    duration: Double,
    sprite: List<SpriteCue>,
    gestures: ScrubGestures,
): Modifier = this
    .pointerInput(duration, sprite) { trackHover(gestures) }
    .pointerInput(duration, sprite) { detectTapGestures { at -> gestures.tap(at.x) } }
    .pointerInput(duration, sprite) {
        detectDragGestures(
            onDragStart = { at ->
                gestures.begin()
                gestures.drag(at.x)
            },
            onDrag = { change, _ -> gestures.drag(change.position.x) },
            // On RELEASE, wherever the pointer has got to by then. `finalizeScrub`
            // is bound on `document` rather than on the bar, so a drag let go
            // outside the strip still commits — and Compose keeps a pointer's hit
            // path until it comes up, which makes onDragEnd the same statement.
            onDragEnd = { gestures.finish(true) },
            onDragCancel = { gestures.finish(false) },
        )
    }

// A pointer resting on the strip, which has no gesture detector of its own.
//
// Read on the Initial pass and never consumed, so the tap and drag detectors still
// see everything. A pressed pointer belongs to the drag, and a touch has no hover
// at all — which is exactly what `@media (hover: hover)` says about the growth
// this drives, without having to ask the platform what kind of device it is.
private suspend fun PointerInputScope.trackHover(gestures: ScrubGestures) {
    awaitPointerEventScope {
        while (true) {
            val event: PointerEvent = awaitPointerEvent(PointerEventPass.Initial)
            val change: PointerInputChange = event.changes.firstOrNull() ?: continue

            when (hoverMoveOf(event, change)) {
                HoverMove.IGNORE -> Unit
                HoverMove.LEFT -> gestures.hover(null)
                HoverMove.AT -> gestures.hover(change.position.x)
            }
        }
    }
}

// What one pointer event says about hovering, if anything.
//
// Read out of the event rather than decided inside the loop, so the loop is a
// dispatch and this is the rule. Inlined it was a chain of conditions detekt
// counted as ten branches, which is the point at which nobody checks it by eye.
private enum class HoverMove { IGNORE, AT, LEFT }

private fun hoverMoveOf(event: PointerEvent, change: PointerInputChange): HoverMove = when {
    // A pressed pointer is the drag's, and a touch has no hover at all — which is
    // exactly what `@media (hover: hover)` says about the growth this drives,
    // without having to ask the platform what kind of device it is.
    change.pressed || change.type == PointerType.Touch -> HoverMove.IGNORE
    event.type == PointerEventType.Exit -> HoverMove.LEFT
    event.type == PointerEventType.Enter || event.type == PointerEventType.Move -> HoverMove.AT
    else -> HoverMove.IGNORE
}

// Where along the film a horizontal position falls.
//
// Clamped at both ends, because a drag that leaves the strip reports a position
// outside it and an unclamped answer is a seek past the end or before the start.
internal fun secondsAt(x: Float, width: Float, durationSeconds: Double): Double {
    if (durationSeconds <= 0.0 || width <= 0f) return 0.0

    val fraction: Double = (x / width).toDouble().coerceIn(0.0, 1.0)
    return fraction * durationSeconds
}

internal const val SCRUBBER_TAG = "nm-scrubber"

// Taller than the bar it draws. Fingers are not pixels.
internal val SCRUBBER_TOUCH_HEIGHT: Dp = 32.dp

// `.slider-bar`'s own `transition: height 140ms ease-out`. CSS `ease-out` is
// `cubic-bezier(0, 0, 0.58, 1)`, written out rather than approximated with one of
// Compose's named curves — those are Material's motion, not this stylesheet's.
private const val BAR_GROW_MS: Int = 140
private val BAR_GROW_EASING: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

// The drawn bar, as its own composable.
//
// Split because the scrubber is at its length limit and this is the part that says
// WHICH bar: two ChapterProgressBar composables exist and the wrong one was being
// drawn, so every fix to the segmented drawing — the chapter palette, the
// per-segment buffer, the 2px corners and the minimum width — landed on a copy the
// desktop chrome never renders. A duplicate is worse than a missing component: the
// fix looks applied, the gate reads the file it was applied to, and the screen
// shows the other one.
@Composable
private fun ScrubberBar(state: ChromeState, track: ScrubTrack, height: Dp) {
    ChapterProgressBar(
        state = ChapterBarState(
            currentSeconds = track.drag ?: state.timeSeconds,
            duration = state.durationSeconds,
            bufferedFraction = state.bufferedFraction.toDouble(),
            chapters = state.chapters.map { Chapter(startTime = it.startSeconds, title = it.title.orEmpty()) },
            // Null the moment the pointer leaves or the drag ends, which is
            // `mouseleave` resetting every `.chapter-marker-hover` to scaleX(0).
            // Left set, the last place a viewer looked stayed lit on the bar and
            // went on advertising a position nobody was pointing at.
            hoverSeconds = track.hunted,
        ),
        // Null: this composable owns the gesture and seeks once on release.
        onSeek = null,
        modifier = Modifier.fillMaxWidth(),
        height = height,
    )
}
