// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

// The payloads only a video player has. Everything else a video player emits is
// a core event with a core payload, which is the point of the split: the video
// library adds a surface, it does not restate one.

// How the picture is fitted to the surface it is drawn on.
public enum class Stretching(public val token: String) {
    Uniform("uniform"),
    UniformFill("uniformFill"),
    Fill("fill"),
    None("none"),
    ;

    public companion object {
        public fun fromToken(token: String): Stretching =
            entries.firstOrNull { it.token == token }
                ?: throw IllegalArgumentException("unknown Stretching token: $token")
    }
}

public data class AspectRatioChange(val value: Stretching)

// One selectable audio track. Descriptor-keyed rather than index-keyed: an
// engine reorders its track list between loads and an index taken before a
// reload points at something else afterwards.
public data class AudioTrack(
    val id: String,
    val language: String? = null,
    val label: String? = null,
    val channels: Int? = null,
    val codec: String? = null,
)

public data class AudioTracksChange(val tracks: List<AudioTrack>)

// One rung of the quality ladder. Height and bitrate together, never an index —
// the same reason as audio tracks, and the ladder changes shape between streams.
public data class QualityLevel(
    val height: Int,
    val bitrate: Int,
    val codec: String? = null,
    val dynamicRange: String? = null,
)

public data class LevelsChange(val levels: List<QualityLevel>)

// The requested rung, or null for automatic. Null rather than a sentinel number
// so "let ABR decide" cannot be confused with a real level.
public data class QualityRequest(val level: Int?)

// A message for the viewer, and how long to leave it up. Null ms means the
// consumer decides.
public data class DisplayMessage(val text: String, val ms: Double? = null)

// Where the picture actually sits inside its surface, in pixels. Null when
// nothing is rendered yet, which a chrome positioning an overlay has to handle.
public data class VideoRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

public data class VideoRectChange(val rect: VideoRect?)

// A named region of an item — an intro, a recap, credits — and what to offer
// the viewer when the playhead reaches it.
public data class SegmentBoundary(
    val id: String,
    val kind: String,
    val startTime: Double,
    val endTime: Double,
    val label: String? = null,
)

// Fullscreen, picture-in-picture and theater all answer the same question, so
// they share a payload rather than each having a one-field class.
public data class ViewModeChange(val active: Boolean)
