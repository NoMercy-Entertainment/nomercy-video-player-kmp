// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.preferences

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.core.ports.Storage

// Which rung the viewer picked, or that they picked Auto.
//
// Height rather than a stored QualityLevel, because the ladder a device is
// offered is filtered by what that device can decode: the 1080p rung on a phone
// and the 1080p rung on a desktop differ in bitrate and codec, and a saved
// descriptor would match neither after a device change. Height is the thing the
// viewer chose from a menu.
@Serializable
public data class SavedQuality(
    val auto: Boolean,
    val height: Int = 0,
)

// The reading and writing, kept away from the plugin.
//
// Same split as EqualizerStore: the plugin's subject is which events mean a
// preference changed and when a restore is due, and serialising nine fields is
// a second subject that would double the file it lives in.
internal class VideoPreferencesStore(private val storage: Storage) {

    suspend fun style(): SubtitleStyle? = storage.getJSON(STYLE, StoredSubtitleStyle.serializer())?.toStyle()

    suspend fun saveStyle(style: SubtitleStyle) {
        storage.setJSON(STYLE, StoredSubtitleStyle.of(style), StoredSubtitleStyle.serializer())
    }

    suspend fun volume(): Int? = storage.getJSON(VOLUME, Int.serializer())

    suspend fun saveVolume(level: Int) {
        storage.setJSON(VOLUME, level.coerceIn(MIN_VOLUME, MAX_VOLUME), Int.serializer())
    }

    suspend fun muted(): Boolean? = storage.getJSON(MUTED, Boolean.serializer())

    suspend fun saveMuted(muted: Boolean) {
        storage.setJSON(MUTED, muted, Boolean.serializer())
    }

    // A row written before picks carried a marker cannot say who chose it. A Signs
    // or Forced kind in such a row is read as no kind at all, so the restore lands
    // on the full track of the language. Every reader sees this one answer.
    suspend fun subtitle(): SavedSubtitle? =
        storage.getJSON(SUBTITLE, SavedSubtitle.serializer())?.let { saved ->
            if (saved.explicit || saved.kind !in UNCHOSEN_KINDS) saved else saved.copy(kind = null, format = null)
        }

    // Language alone is not the choice. A viewer who picked English SDH and got
    // plain English back was handed a different track under the same name, so
    // the variant and the file's format travel with it.
    suspend fun saveSubtitle(subtitle: SavedSubtitle?) {
        if (subtitle == null) {
            storage.remove(SUBTITLE)
        } else {
            storage.setJSON(SUBTITLE, subtitle, SavedSubtitle.serializer())
        }
    }

    suspend fun audio(): String? = storage.getJSON(AUDIO, String.serializer())

    suspend fun saveAudio(language: String?) {
        write(AUDIO, language)
    }

    suspend fun quality(): SavedQuality? = storage.getJSON(QUALITY, SavedQuality.serializer())

    suspend fun saveQuality(quality: SavedQuality) {
        storage.setJSON(QUALITY, quality, SavedQuality.serializer())
    }

    // A track turned off is a preference. Removing the key instead would restore
    // the last language the viewer had chosen before switching captions off,
    // which is the opposite of what they asked for.
    private suspend fun write(key: String, language: String?) {
        if (language == null) storage.remove(key) else storage.setJSON(key, language, String.serializer())
    }
}

// The style, in a shape that can be written down.
//
// SubtitleStyle is a plain data class in core with no serializer, and adding one
// there would put a serialization dependency on a type every consumer of the
// event bus touches. Mirroring nine fields once, here, keeps that cost inside
// the plugin that needs it.
@Serializable
internal data class StoredSubtitleStyle(
    val fontSize: Int,
    val fontFamily: String,
    val textColor: String,
    val textOpacity: Int,
    val backgroundColor: String,
    val backgroundOpacity: Int,
    val edgeStyle: String,
    val areaColor: String,
    val windowOpacity: Int,
) {
    fun toStyle(): SubtitleStyle = SubtitleStyle(
        fontSize = fontSize,
        fontFamily = fontFamily,
        textColor = textColor,
        textOpacity = textOpacity,
        backgroundColor = backgroundColor,
        backgroundOpacity = backgroundOpacity,
        edgeStyle = edgeStyle,
        areaColor = areaColor,
        windowOpacity = windowOpacity,
    )

    companion object {
        fun of(style: SubtitleStyle): StoredSubtitleStyle = StoredSubtitleStyle(
            fontSize = style.fontSize,
            fontFamily = style.fontFamily,
            textColor = style.textColor,
            textOpacity = style.textOpacity,
            backgroundColor = style.backgroundColor,
            backgroundOpacity = style.backgroundOpacity,
            edgeStyle = style.edgeStyle,
            areaColor = style.areaColor,
            windowOpacity = style.windowOpacity,
        )
    }
}

private const val STYLE = "subtitle-style"
private const val VOLUME = "volume"
private const val MUTED = "muted"
private const val SUBTITLE = "subtitle"
private const val AUDIO = "audio"
private const val QUALITY = "quality"

private const val MIN_VOLUME = 0
private const val MAX_VOLUME = 100

// The kinds a player picks by itself when it has nothing else to go on.
private val UNCHOSEN_KINDS: Set<String?> = setOf("Signs", "Forced")

/**
 * A caption choice: the language, the variant it was, and the file's format.
 *
 * [explicit] is true when the viewer picked the track. A row without it (an older
 * build, or a default the player picked) restores as the full track.
 */
@Serializable
public data class SavedSubtitle(
    val language: String,
    val kind: String? = null,
    val format: String? = null,
    val explicit: Boolean = false,
)
