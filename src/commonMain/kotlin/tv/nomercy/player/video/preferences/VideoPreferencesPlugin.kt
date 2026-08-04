// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.preferences

import kotlinx.coroutines.Job
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.core.player.ActionOptions
import tv.nomercy.player.core.player.ActionSource
import tv.nomercy.player.core.plugin.Plugin
import tv.nomercy.player.core.plugin.PluginManifest
import tv.nomercy.player.core.plugin.PluginOptionField
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.VideoEvents

// What the viewer chose last time, applied to what they are watching now.
//
// The web carries this in its testbed with a note that it belongs in the kit.
// Native has no equivalent deferral to inherit, so it lands here: every method
// it needs is on NMVideoPlayer, and a consumer who wants their subtitle
// language to survive a restart should not have to write the same forty lines.
//
// Selection is by DESCRIPTOR, never by index. The web restores "subtitle track
// 2" and the native ladder is filtered by device capability, so position two in
// one list is a different track in the other — restoring an index would quietly
// give a viewer the wrong language on the device that filtered hardest. What is
// stored is the language, and what is restored is the track that has it.
public open class VideoPreferencesPlugin(
    private val player: NMVideoPlayer,
    opts: VideoPreferencesOptions = VideoPreferencesOptions(),
) : Plugin<VideoPreferencesOptions>() {

    public companion object Manifest : PluginManifest {
        override val id: String = "video-preferences"
        override val version: String = "1.0.0"
    }

    override val manifest: PluginManifest get() = Manifest

    // Held rather than fixed: turning a restore off has to be something the
    // plugin reads on the next item, not a control that moves and does nothing.
    private var opts: VideoPreferencesOptions = opts

    override val options: VideoPreferencesOptions get() = opts

    override fun optionFields(): List<PluginOptionField> = listOf(
        PluginOptionField.Toggle(
            key = "restoreSubtitle",
            label = "Restore subtitle",
            value = opts.restoreSubtitle,
            apply = { on -> opts = opts.copy(restoreSubtitle = on) },
        ),
        PluginOptionField.Toggle(
            key = "restoreAudio",
            label = "Restore audio track",
            value = opts.restoreAudio,
            apply = { on -> opts = opts.copy(restoreAudio = on) },
        ),
        PluginOptionField.Toggle(
            key = "restoreQuality",
            label = "Restore quality",
            value = opts.restoreQuality,
            apply = { on -> opts = opts.copy(restoreQuality = on) },
        ),
        PluginOptionField.Toggle(
            key = "restoreVolume",
            label = "Restore volume",
            value = opts.restoreVolume,
            apply = { on -> opts = opts.copy(restoreVolume = on) },
        ),
        PluginOptionField.Toggle(
            key = "restoreSubtitleStyle",
            label = "Restore subtitle style",
            value = opts.restoreSubtitleStyle,
            apply = { on -> opts = opts.copy(restoreSubtitleStyle = on) },
        ),
    )

    private val store: VideoPreferencesStore get() = VideoPreferencesStore(storage)

    override fun use() {
        on(CoreEvents.SubtitleStyle) { style -> remember { store.saveStyle(style) } }
        on(CoreEvents.Volume) { change -> remember { store.saveVolume(change.level) } }
        on(CoreEvents.Mute) { change -> remember { store.saveMuted(change.muted) } }

        // The player is read at event time rather than the payload resolved.
        //
        // Both payloads carry an index into a list the player already holds, and
        // asking the player which track is selected cannot disagree with the
        // player. Resolving the index here would be a second answer to a
        // question that already has one.
        on(CoreEvents.Subtitle) { remember { store.saveSubtitle(player.subtitle()?.language) } }
        on(CoreEvents.AudioTrack) { remember { store.saveAudio(player.audioTrack()?.language) } }

        // The viewer's choice, not the ladder's. `quality:requested` fires on an
        // explicit pick; a level-switch event would persist whatever ABR landed
        // on and drag the viewer out of Auto on the next load without them ever
        // having left it.
        on(VideoEvents.QualityRequested) { remember { store.saveQuality(chosenQuality()) } }

        on(CoreEvents.Item) { remember { restore() } }

        remember {
            applyStyle()
            applyVolume()
            restore()
        }
    }

    // Writing is fire-and-forget, and the handle is kept so it can be waited on.
    //
    // A viewer never waits for a preference to be written — the choice has
    // already taken effect in the player and the storage write is bookkeeping.
    // But a test that changes a track and then asks what was remembered is
    // racing that write, and the alternatives are worse: driving the player on
    // the test scheduler hangs, because the player's own metrics and progress
    // intervals never let virtual time reach idle.
    private fun remember(block: suspend () -> Unit) {
        lastWrite = launch { block() }
    }

    private var lastWrite: Job? = null

    internal suspend fun awaitWrites() {
        lastWrite?.join()
    }

    // Everything that survives across items. Applied once at install and again
    // on every item, because a new item brings new track lists.
    public open suspend fun restore() {
        if (opts.restoreSubtitle) restoreSubtitle()
        if (opts.restoreAudio) restoreAudio()
        if (opts.restoreQuality) restoreQuality()
    }

    private suspend fun applyStyle() {
        if (!opts.restoreSubtitleStyle) return
        val style: SubtitleStyle = store.style() ?: return
        player.subtitleStyle(style)
    }

    private suspend fun applyVolume() {
        if (!opts.restoreVolume) return
        store.volume()?.let { level: Int -> player.volume(level, restored) }
        if (store.muted() == true) player.mute(restored)
    }

    private suspend fun restoreSubtitle() {
        val language: String = store.subtitle() ?: return
        val track: SubtitleTrack = player.subtitles().firstOrNull { it.language == language } ?: return
        player.subtitle(track)
    }

    private suspend fun restoreAudio() {
        val language: String = store.audio() ?: return
        val track: AudioTrack = player.audioTracks().firstOrNull { it.language == language } ?: return
        player.audioTrack(track)
    }

    // Auto is a stored value, not the absence of one.
    //
    // A viewer who pinned 1080p and then went back to Auto has made a choice
    // both times. Treating the second as "nothing saved" would re-pin 1080p on
    // the next load, which is the same bug as never having saved anything.
    private suspend fun restoreQuality() {
        val saved: SavedQuality = store.quality() ?: return
        if (saved.auto) {
            player.quality(null)
            return
        }
        val level: QualityLevel = player.qualityLevels().firstOrNull { it.height == saved.height } ?: return
        player.quality(level)
    }

    // Read from the player rather than from the request's index, for the reason
    // the track handlers above give: the payload indexes a list the player is
    // already holding, and a pin the engine refused would be stored as accepted.
    private fun chosenQuality(): SavedQuality {
        val level: QualityLevel? = player.quality()
        return if (level == null) SavedQuality(auto = true) else SavedQuality(auto = false, height = level.height)
    }
}

// Marked as ours so a consumer watching every volume change can tell the one it
// caused from the one the plugin replayed at startup.
private val restored: ActionOptions = ActionOptions(source = ActionSource.PLUGIN, silent = true)
