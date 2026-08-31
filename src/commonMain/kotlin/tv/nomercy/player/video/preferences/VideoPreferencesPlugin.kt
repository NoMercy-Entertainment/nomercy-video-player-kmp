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
import tv.nomercy.player.core.ports.subtitleKindOf
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
        // Saved on every selection, with no window and no guard.
        //
        // `subtitle` and `audioTrack` are emitted by the SETTER and by nothing
        // else — an engine settling on the file's own default does not fire
        // them. A guard against "the engine's default arriving like a tap" was
        // therefore protecting against something that cannot happen, and what it
        // actually dropped was the viewer's own pick: a language saved once
        // could not be corrected, and which language an item opened in depended
        // on what had been played before it. This is the shape the retired
        // Android player used, which is the one that worked.
        on(CoreEvents.Subtitle) {
            if (applying) return@on
            remember {
                val track: SubtitleTrack? = player.subtitle()
                store.saveSubtitle(
                    track?.let {
                        SavedSubtitle(
                            language = it.language,
                            kind = subtitleKindOf(it.label),
                            format = it.format,
                        )
                    },
                )
            }
        }
        on(CoreEvents.AudioTrack) {
            if (applying) return@on
            remember { store.saveAudio(player.audioTrack()?.language) }
        }

        // The viewer's choice, not the ladder's. `quality:requested` fires on an
        // explicit pick; a level-switch event would persist whatever ABR landed
        // on and drag the viewer out of Auto on the next load without them ever
        // having left it.
        on(VideoEvents.QualityRequested) { remember { store.saveQuality(chosenQuality()) } }

        // MediaReady, not Item. Item fires when the CURSOR moves — before the new
        // source is loaded — so subtitles() was still empty and every restore on
        // an episode change silently found nothing to select.
        //
        // And MediaReady is still not late enough on every engine. It means the
        // source is loaded and will accept a seek; the track list arrives when
        // the engine has parsed it, which can be after. `restoreAudio` gave up
        // silently on an empty list, so an episode opened in whatever language
        // the file defaults to — Japanese, on a viewer who had chosen English.
        // The want is held until a list exists to satisfy it.
        on(CoreEvents.Item) {
            audioPending = true
            subtitlePending = true
            // An item carries its own sidecar subtitles, so that list can already
            // be there. The audio list comes from the manifest and is not.
            remember { restoreWhatIsOwed() }
        }
        on(CoreEvents.MediaReady) { remember { restoreWhatIsOwed() } }
        // The lists themselves, which is when a restore can actually succeed.
        // Waiting for the clock instead left the want open on a player that was
        // paused, and every pick made while it was open was dropped as if it had
        // been the engine's.
        on(CoreEvents.Subtitles) { remember { restoreWhatIsOwed() } }
        on(VideoEvents.AudioTracks) { remember { restoreWhatIsOwed() } }
        on(CoreEvents.Time) {
            if (audioPending || subtitlePending) remember { restoreWhatIsOwed() }
        }

        remember {
            applyStyle()
            applyVolume()
            restore()
        }
    }

    /**
     * The audio language the viewer last chose, before any track list exists.
     *
     * A server that transcodes on demand decides which rendition it marks
     * default, and it decides that when the SESSION opens — before the player
     * has a list to restore against. A consumer asking for that session has to
     * be able to say which language, or the choice is made for it and the
     * restore is left correcting an item that opened in the wrong one.
     */
    public suspend fun savedAudioLanguage(): String? = store.audio()

    /**
     * Adopt a language chosen on another device as this one's choice.
     *
     * A handoff carries the language the viewer was listening to, and the device
     * taking over has to know it before it opens its own transcode session —
     * which is before there is any track list to restore against.
     */
    public suspend fun rememberAudioLanguage(language: String?) {
        if (language.isNullOrBlank()) return
        store.saveAudio(language)
    }

    /** The caption choice the viewer last made, for the same reason. */
    public suspend fun savedSubtitle(): SavedSubtitle? = store.subtitle()

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

    // The same restore, limited to the kinds still owed one.
    //
    // An item change asks for both and the two lists answer at different times,
    // so a tick that already restored the audio must not walk over a language
    // the viewer has since picked by hand.
    private suspend fun restoreWhatIsOwed() {
        if (opts.restoreSubtitle && subtitlePending) restoreSubtitle()
        if (opts.restoreAudio && audioPending) restoreAudio()
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

    // Narrowest match first. A ladder filtered by device capability can drop the
    // exact variant, and a viewer is better served by the same language in a
    // different flavour than by no captions at all.
    private suspend fun restoreSubtitle() {
        val available: List<SubtitleTrack> = player.subtitles()
        if (available.isEmpty()) return
        subtitlePending = false
        val saved: SavedSubtitle = store.subtitle() ?: return

        val track: SubtitleTrack = available.firstOrNull {
            it.language == saved.language &&
                subtitleKindOf(it.label) == saved.kind &&
                it.format == saved.format
        } ?: available.firstOrNull {
            it.language == saved.language && subtitleKindOf(it.label) == saved.kind
        } ?: available.firstOrNull {
            it.language == saved.language
        } ?: return

        applySelection { player.subtitle(track) }
    }

    // Whether a restore is still owed for that kind, from the cursor move until
    // a real list has been offered one. Held per kind because the two lists do
    // not arrive together: gating both on the audio list let a subtitle restore
    // run against an empty list and count itself answered.
    private var audioPending: Boolean = false
    private var subtitlePending: Boolean = false

    // True only while the plugin itself is selecting a track.
    //
    // The restore goes through the same setter a viewer's tap does, and that
    // setter is what announces a selection — so without this the plugin writes
    // back what it has just read, and a write racing a tap can put the OLD
    // language back on top of the new one.
    private var applying: Boolean = false

    private suspend fun applySelection(select: suspend () -> Unit) {
        applying = true
        try {
            select()
        } finally {
            applying = false
        }
    }

    private suspend fun restoreAudio() {
        val available: List<AudioTrack> = player.audioTracks()
        if (available.isEmpty()) return
        audioPending = false
        val language: String = store.audio() ?: return
        val track: AudioTrack = available.firstOrNull { it.language == language } ?: return
        applySelection { player.audioTrack(track) }
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
