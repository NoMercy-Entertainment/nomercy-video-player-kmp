// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitleCue
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest

/** How tall a cue box is, as a percentage of the safe area. */
public data class SubtitleOverlayOptions(
    val cueHeightPercent: Double = DEFAULT_CUE_HEIGHT_PERCENT,
)

/**
 * Positions the cues a chrome draws.
 *
 * The same `subtitle-overlay` id the web plugin has, so a consumer moving code
 * across writes the same line:
 *
 *     player.addPlugin(SubtitleOverlayPlugin())
 *
 * This is WebVTT and SRT. ASS carries its own positioning and is drawn by the
 * `subtitles-libass` module, which is a module rather than a plugin because
 * libass arrives through cinterop.
 *
 * It publishes boxes and draws nothing, which is the seam the web has too: the
 * web plugin owns a DOM tree because a browser gives it one, and here the
 * Compose and SwiftUI chromes each render [boxes] their own way. What both get
 * from this is the same numbers — [layOutCues] decides them, and it is the same
 * function on every platform.
 */
public open class SubtitleOverlayPlugin(
    private val opts: SubtitleOverlayOptions = SubtitleOverlayOptions(),
) : Plugin<SubtitleOverlayOptions>() {

    public companion object Manifest : PluginManifest {
        override val id: String = "subtitle-overlay"

        // Two, matching the web plugin this mirrors.
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    override val options: SubtitleOverlayOptions get() = opts

    private val mutable: MutableStateFlow<List<CueBox>> = MutableStateFlow(emptyList())

    /** The cues on screen right now, laid out and separated. */
    public val boxes: StateFlow<List<CueBox>> = mutable.asStateFlow()

    /**
     * The one channel cues arrive on, and the item change that clears them.
     *
     * This plugin used to be caller-driven only, on the reasoning that "which
     * event carries cues depends on where they came from — a sidecar file, an
     * embedded track, a consumer's own parser — and a plugin that picked one
     * would work for a third of them." The web overlay answers that directly:
     * it subscribes to `subtitleCue` alone, and the comment beside it says why —
     * the kit and the backend funnel BOTH the sidecar and the engine's own cues
     * into that single channel, so there is one event to pick and picking it
     * works for all of them.
     *
     * Left caller-driven, the plugin had no production driver at all: nothing
     * called [show] outside a test, so the flow the renderer reads was
     * permanently empty and a correct renderer drew nothing.
     *
     * [show] stays public for a caller with richer cues than the event carries.
     */
    override fun use() {
        on(CoreEvents.SubtitleCue) { change -> show(change.cues) }

        // The web's `this.on('item', () => this.renderCues([]))`. Belt and
        // braces — the producer normally sends an empty change on unload — but
        // the failure it covers is the one nobody would report as a bug: the
        // last line of the previous film sitting over the first frame of the
        // next one.
        on(CoreEvents.Item) { clear() }
    }

    /**
     * Hand it the cues active at this moment; it lays them out.
     *
     * Public as well as driven by [use], for a consumer rendering cues from a
     * source of their own. Nothing in this library needs it any more: the event
     * carries the full cue list with its WebVTT positioning, so a producer no
     * longer has to go around the channel every other listener reads to keep it.
     */
    public fun show(cues: List<SubtitleCue>) {
        mutable.value = layOutCues(cues, opts.cueHeightPercent)
    }

    /** Nothing on screen. */
    public fun clear() {
        mutable.value = emptyList()
    }

    override fun dispose() {
        clear()
    }
}

/**
 * A default that reads as two lines of dialogue at a normal size.
 *
 * A number rather than a measurement because this file draws nothing and cannot
 * measure text. A chrome that knows its own font passes its own.
 */
public const val DEFAULT_CUE_HEIGHT_PERCENT: Double = 8.0
