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
     * Hand it the cues active at this moment; it lays them out.
     *
     * Driven by the caller rather than by subscribing to a cue event, because
     * which event carries cues depends on where they came from — a sidecar
     * file, an embedded track the engine reports, a consumer's own parser — and
     * a plugin that picked one would work for a third of them.
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
