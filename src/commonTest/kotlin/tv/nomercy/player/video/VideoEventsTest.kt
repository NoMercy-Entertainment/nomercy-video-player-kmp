// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.EventEmitter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoEventsTest {

    @Test
    fun everyKeyNameIsTheWebVideoEventMapKeyVerbatim() {
        assertEquals(
            listOf(
                "aspectRatio", "audioTracks", "back", "canplay", "cast", "close",
                "display-message", "fullscreen", "levels", "pip", "quality:requested",
                "remove-message", "segmentBoundary", "stalled", "subtitle-size-down",
                "subtitle-size-up", "theater", "videoRect", "waiting",
            ),
            VideoEvents.all.map { it.name },
        )
    }

    @Test
    fun theVideoRegistryAddsToTheCoreOneRatherThanRestatingIt() {
        val core = CoreEvents.all.map { it.name }.toSet()
        val video = VideoEvents.all.map { it.name }.toSet()

        // An event declared in both would mean two payload types for one wire
        // name, and whichever library the listener imported from would win.
        assertEquals(emptySet(), core intersect video)
    }

    @Test
    fun noNameIsRegisteredTwice() {
        val names = VideoEvents.all.map { it.name }

        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun aVideoEventDeliversOnTheSameBusACoreEventDoes() {
        val bus = EventEmitter<Unit>()
        var fullscreen: Boolean? = null
        var played = false
        bus.on(VideoEvents.Fullscreen) { fullscreen = it.active }
        bus.on(CoreEvents.Play) { played = true }

        bus.emit(VideoEvents.Fullscreen, ViewModeChange(active = true))
        bus.emit(CoreEvents.Play, tv.nomercy.player.core.events.PlaySource())

        // One bus, one set of names. The split is about which library owns the
        // declaration, not about where the event travels.
        assertEquals(true, fullscreen)
        assertTrue(played)
    }

    @Test
    fun anAutomaticQualityRequestIsNullRatherThanASentinel() {
        val automatic = QualityRequest(level = null)
        val pinned = QualityRequest(level = 1080)

        // A sentinel number would be indistinguishable from a real level.
        assertEquals(null, automatic.level)
        assertEquals(1080, pinned.level)
    }

    @Test
    fun aQualityLevelIsDescribedByHeightAndBitrateNotByAnIndex() {
        val level = QualityLevel(height = 1080, bitrate = 6_000_000, codec = "hevc")

        // An engine reorders its ladder between streams, so an index taken
        // before a reload points at something else afterwards.
        assertEquals(1080, level.height)
        assertEquals(6_000_000, level.bitrate)
    }

    @Test
    fun aStretchingTokenRoundTrips() {
        Stretching.entries.forEach { assertEquals(it, Stretching.fromToken(it.token)) }
    }
}
