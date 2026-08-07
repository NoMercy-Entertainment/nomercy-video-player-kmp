// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import tv.nomercy.player.core.device.DeviceCapabilities
import tv.nomercy.player.core.input.KeyHandlerScope
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.core.input.asCombo
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.plugin.PluginOptionField
import tv.nomercy.player.video.chapters.resolveChapterLabel

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
    tvOptions: TvKeyHandlerOptions = TvKeyHandlerOptions(),
    // A television is the one place Container scope is the right default: the
    // remote's arrows move focus through a grid of rows, and a player that
    // claimed them from anywhere in the window would swallow navigation the
    // moment it was mounted behind something else.
    scope: KeyHandlerScope = KeyHandlerScope.Container,
) : VideoKeyHandlerPlugin(commands, capabilities, scope, nowMs) {

    public companion object Manifest : PluginManifest {
        override val id: String = "tv-key-handler"
        override val version: String = "2.0.0"
    }

    // Held rather than fixed: the arrow step is the option somebody changes
    // while sitting in front of a television, which means it is read again.
    private var tvOptions: TvKeyHandlerOptions = tvOptions

    override val manifest: PluginManifest get() = Manifest

    override fun optionFields(): List<PluginOptionField> = listOf(
        PluginOptionField.Number(
            key = "arrowSeekSeconds",
            label = "Arrow seek (seconds)",
            value = tvOptions.arrowSeekSeconds.toDouble(),
            min = MIN_ARROW_SEEK,
            max = MAX_ARROW_SEEK,
            apply = { chosen -> tvOptions = tvOptions.copy(arrowSeekSeconds = chosen.toInt()) },
        ),
        PluginOptionField.Number(
            key = "infoDisplayMs",
            label = "Info panel duration (ms)",
            value = tvOptions.infoDisplayMs.toDouble(),
            min = MIN_INFO_MS,
            max = MAX_INFO_MS,
            step = INFO_STEP_MS,
            apply = { chosen -> tvOptions = tvOptions.copy(infoDisplayMs = chosen.toLong()) },
        ),
    )

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
            commands.presentation.message(tvOptions.aspectRatioWord)
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

    private fun summaryOf(): TvPlaybackSummary {
        val position: Double = commands.state.time()
        val total: Double = commands.state.duration()

        return TvPlaybackSummary(
            timeSeconds = position,
            durationSeconds = total,
            displayForMs = tvOptions.infoDisplayMs,
            // The item's own name, or the word the host gave for nothing named.
            // Blank counts as unnamed: a server sending an empty title is not a
            // server naming the item "".
            title = commands.state.title()?.takeIf { it.isNotBlank() } ?: tvOptions.noTitleWord,
            // Never negative. An engine reporting a position past a duration it
            // has not refreshed yet is ordinary at the end of an item.
            remainingSeconds = (total - position).coerceAtLeast(0.0),
            chapterLabel = resolveChapterLabel(
                commands.state.chapters(),
                position,
                tvOptions.chapterWord,
            ),
        )
    }
}

public data class TvKeyHandlerOptions(
    // Five seconds. Short enough to be useful without a modifier, and the
    // coloured buttons are there for anything longer.
    val arrowSeekSeconds: Int = 5,
    val infoDisplayMs: Long = 5_000,
    /**
     * The viewer's language, as a tag [TvKeyHandlerTranslations] is keyed by. The
     * three words below are read out of that table for it.
     *
     * Pass the viewer's own. This defaults to English because commonMain has no
     * ambient locale to read — the chrome gets one from Compose through
     * `rememberChromeLocale()` and there is no equivalent outside a composition —
     * so a construction site that leaves it out ships English to every viewer.
     * That is how the chrome's own strings came to be English in all 79 locales
     * for a while: the table was there and every caller took the default.
     */
    val locale: String = TvKeyHandlerTranslations.FALLBACK,
    /** The word in front of a chapter number on the info panel. */
    val chapterWord: String = TvKeyHandlerTranslations.get(locale, KEY_CHAPTER),
    /** What the panel reads when the item carries no title. */
    val noTitleWord: String = TvKeyHandlerTranslations.get(locale, KEY_NO_TITLE),
    /**
     * What the OSD says when the picture changes shape.
     *
     * This was "aspect ratio changed", written here rather than taken from the
     * web table, which is a rewording of a string the web already had in 79
     * languages — the fidelity failure the generated table exists to stop.
     */
    val aspectRatioWord: String = TvKeyHandlerTranslations.get(locale, KEY_ASPECT_RATIO),
)

// What the info button reports.
//
// The remaining time and the chapter are here because the web's `info` payload
// carries them and a viewer on a television has nowhere else to read either. The
// two numbers alone are what this sent, so a film with chapters announced a
// position and left the viewer to work out where in the film that was.
public data class TvPlaybackSummary(
    val timeSeconds: Double,
    val durationSeconds: Double,
    val displayForMs: Long,
    val remainingSeconds: Double = 0.0,
    /** Empty when the item has no chapters, or none has started yet. */
    val chapterLabel: String = "",
    val title: String = "",
)

public data class TvBookmark(val timeSeconds: Double)

// The web plugin's own keys, so the two players read the same value for the same
// thing rather than one of them a paraphrase of it.
private const val KEY_CHAPTER = "plugin.tv-key-handler.info.chapter"
private const val KEY_NO_TITLE = "plugin.tv-key-handler.info.noTitle"
private const val KEY_ASPECT_RATIO = "plugin.tv-key-handler.aspectRatio.cycled"

// What an editor offers a remote. One second is a frame-hunt and sixty is a
// scene; an info panel under a second is a flash and past thirty it is furniture.
private const val MIN_ARROW_SEEK: Double = 1.0
private const val MAX_ARROW_SEEK: Double = 60.0
private const val MIN_INFO_MS: Double = 1_000.0
private const val MAX_INFO_MS: Double = 30_000.0
private const val INFO_STEP_MS: Double = 500.0
