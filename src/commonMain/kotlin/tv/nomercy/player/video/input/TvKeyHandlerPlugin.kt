// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import tv.nomercy.player.core.device.DeviceCapabilities
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.core.input.asCombo
import tv.nomercy.player.core.plugin.PluginManifest

// The bindings a remote needs that a keyboard does not.
//
// Only the groups that differ are overridden. Everything else is inherited, so
// the coloured buttons, the media keys and the track cycling are the same code
// on a television as anywhere else and cannot drift from it.
//
// It draws nothing. The chrome is separate on purpose: a remote has to work on a
// television that has no chrome mounted at all, and a key handler that assumed
// one would break the moment somebody embedded the player bare.
//
// Everything here calls the local commands. A device following a Connect session
// swaps the individual bindings out rather than subclassing this again, so one
// key can be routed to a hub while the rest stay local.
public open class TvKeyHandlerPlugin(
    commands: PlayerCommands,
    capabilities: DeviceCapabilities,
    nowMs: () -> Long,
    private val tvOptions: TvKeyHandlerOptions = TvKeyHandlerOptions(),
) : VideoKeyHandlerPlugin(commands, capabilities, nowMs) {

    public companion object Manifest : PluginManifest {
        override val id: String = "tv-key-handler"
        override val version: String = "2.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    override fun addDefaults() {
        super.addDefaults()
        addInfoKey()
        addBookmarkKey()
    }

    // A shorter step than a keyboard gets. A remote has no modifier keys to pick
    // a magnitude with, so the one step it does have should be small enough to
    // land on a line of dialogue; the coloured buttons cover the long jumps.
    override fun addNavigationKeys() {
        val step: Float = tvOptions.arrowSeekSeconds.toFloat()

        bindings.replace(PlayerKey.Left.asCombo()) { commands.transport.seekBy(-step) }
        bindings.replace(PlayerKey.Right.asCombo()) { commands.transport.seekBy(step) }
    }

    // Enabled here, unlike everywhere else. Some boxes do deliver their volume
    // keys to the running application rather than to the panel, and on those the
    // arrows are the only volume control a viewer has.
    override fun addVolumeKeys() {
        bindings.replace(PlayerKey.Up.asCombo()) { commands.volume.volumeUp() }
        bindings.replace(PlayerKey.Down.asCombo()) { commands.volume.volumeDown() }
    }

    // Announced as well as done. There is no window title and no status bar on a
    // television, so a picture that changes shape with no explanation reads as
    // the player having broken rather than as a setting having changed.
    override fun addAspectRatioKeys() {
        super.addAspectRatioKeys()

        bindings.replace(PlayerKey.Favorites.asCombo()) {
            commands.presentation.cycleAspectRatio()
            commands.presentation.message(ASPECT_RATIO_CYCLED)
        }
    }

    // The button a viewer presses to find out what they are watching. Emitted
    // rather than drawn, because what an information panel looks like is the
    // chrome's business, and this has to work with no chrome mounted.
    protected open fun addInfoKey() {
        bindings.bind(PlayerKey.Info) { emit(TvKeyEvents.Info, summaryOf()) }
    }

    // Record on a remote has no recorder to talk to, so it marks the spot. It is
    // the one button on a television remote with nothing else to do and a viewer
    // reaching for it is already thinking about coming back here.
    protected open fun addBookmarkKey() {
        bindings.bind(PlayerKey.MediaRecord) {
            emit(TvKeyEvents.Bookmark, TvBookmark(commands.state.time()))
        }
    }

    private fun summaryOf(): TvPlaybackSummary = TvPlaybackSummary(
        timeSeconds = commands.state.time(),
        durationSeconds = commands.state.duration(),
        displayForMs = tvOptions.infoDisplayMs,
    )
}

public data class TvKeyHandlerOptions(
    // Five seconds. Short enough to be useful without a modifier, and the
    // coloured buttons are there for anything longer.
    val arrowSeekSeconds: Int = 5,
    val infoDisplayMs: Long = 5_000,
)

public data class TvPlaybackSummary(
    val timeSeconds: Double,
    val durationSeconds: Double,
    val displayForMs: Long,
)

public data class TvBookmark(val timeSeconds: Double)

private const val ASPECT_RATIO_CYCLED = "aspect ratio changed"
