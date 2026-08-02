// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.preferences

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.testing.FakeVideoBackend
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// That what comes back is the viewer's CHOICE and not its old position.
//
// The web plugin this mirrors stores a language and restores by matching it,
// and a port that stored the index instead would pass any test whose two track
// lists happen to be in the same order. So every case here restores against a
// list deliberately reordered or filtered between the save and the restore:
// each one goes green on an index-based port only by coincidence, and there is
// no coincidence available when the saved language has MOVED.
class VideoPreferencesPluginTest {

    private class Rig(
        val player: NMVideoPlayer,
        val backend: FakeVideoBackend,
        val plugin: VideoPreferencesPlugin,
    )

    // The player runs on its own scope, not the test scheduler.
    //
    // Handing it the scheduler hangs the run outright: the player keeps metrics
    // and progress intervals, runTest advances virtual time until nothing is
    // pending, and an interval is always pending. What the test needs from the
    // plugin instead is awaitWrites(), which is a real handle on a real write
    // rather than a guess at how long one takes.
    private suspend fun rig(opts: VideoPreferencesOptions = VideoPreferencesOptions()): Rig {
        val backend = FakeVideoBackend()
        val player = NMVideoPlayer(backend = backend, video = backend)
        player.setup(PlayerConfig())
        player.queue(listOf(VideoItem(id = "a", url = "https://media.example.test/a.m3u8", title = "A")))

        val plugin = VideoPreferencesPlugin(player, opts)
        player.addPlugin(plugin)
        return Rig(player, backend, plugin)
    }

    @Test
    fun theSubtitleThatComesBackIsTheSavedLANGUAGEEvenAfterItMoves() = runTest {
        val rig: Rig = rig()
        rig.backend.subtitleTracks = listOf(english, dutch, german)
        rig.player.subtitle(dutch)
        rig.plugin.awaitWrites()

        // The same three, in the order a different device would report them.
        // Index one is now German; only a plugin matching on language finds Dutch.
        rig.backend.subtitleTracks = listOf(dutch, german, english)
        rig.plugin.restore()

        assertEquals("nld", rig.player.subtitle()?.language, "the restore followed the old index, not the language")
    }

    @Test
    fun theAudioTrackThatComesBackIsTheSavedLanguageEvenAfterItMoves() = runTest {
        val rig: Rig = rig()
        rig.backend.audio = listOf(audioEnglish, audioDutch)
        rig.player.audioTrack(audioDutch)
        rig.plugin.awaitWrites()

        rig.backend.audio = listOf(audioDutch, audioEnglish)
        rig.plugin.restore()

        assertEquals("nld", rig.player.audioTrack()?.language, "the restore followed the old index, not the language")
    }

    // A language the new item does not have is left alone rather than guessed at.
    @Test
    fun aSavedLanguageTheNextItemDoesNotCarryChangesNothing() = runTest {
        val rig: Rig = rig()
        rig.backend.subtitleTracks = listOf(english, dutch)
        rig.player.subtitle(dutch)
        rig.plugin.awaitWrites()

        // German where Dutch used to be. An index-based restore takes it.
        rig.backend.subtitleTracks = listOf(english, german)
        rig.player.subtitle(english)
        rig.plugin.awaitWrites()
        rig.plugin.restore()

        assertEquals(
            "eng",
            rig.player.subtitle()?.language,
            "a missing language was substituted with whatever was at that index",
        )
    }

    // Auto is a choice, not the absence of one.
    @Test
    fun goingBackToAutoIsRememberedRatherThanReadAsNothingSaved() = runTest {
        val rig: Rig = rig()
        rig.backend.levels = listOf(fullHd, hd)
        rig.player.quality(fullHd)
        rig.plugin.awaitWrites()
        rig.player.quality(null)
        rig.plugin.awaitWrites()

        // Straight at the backend, so the next item starting out pinned is not
        // itself recorded as a choice — going through the player would save
        // 1080p again and the restore would be reading back the wrong write.
        rig.backend.quality(fullHd)
        rig.plugin.restore()

        assertNull(rig.player.quality(), "the pin before the Auto came back, so Auto was stored as nothing")
    }

    @Test
    fun aPinnedRungComesBackByHeightSoADifferentLadderStillMatches() = runTest {
        val rig: Rig = rig()
        rig.backend.levels = listOf(fullHd, hd)
        rig.player.quality(hd)
        rig.plugin.awaitWrites()

        // The same rungs as the device would describe them after a codec change,
        // and in the other order. A saved descriptor matches neither, and index
        // one is now 1080p.
        rig.backend.levels = listOf(hd.copy(bitrate = 3_000_000, codec = "hvc1"), fullHd.copy(bitrate = 9_000_000))
        rig.plugin.restore()

        assertEquals(HD_HEIGHT, rig.player.quality()?.height, "the rung was matched on the whole descriptor")
    }

    @Test
    fun aRestoreTurnedOffLeavesTheTrackWhereTheItemPutIt() = runTest {
        val rig: Rig = rig(VideoPreferencesOptions(restoreSubtitle = false))
        rig.backend.subtitleTracks = listOf(english, dutch)
        rig.player.subtitle(dutch)
        rig.plugin.awaitWrites()

        rig.player.subtitle(english)
        rig.plugin.awaitWrites()
        rig.plugin.restore()

        assertEquals("eng", rig.player.subtitle()?.language, "the switch was drawn and read by nothing")
    }
}

private val english = SubtitleTrack(id = "s-eng", language = "eng", label = "English")
private val dutch = SubtitleTrack(id = "s-nld", language = "nld", label = "Nederlands")
private val german = SubtitleTrack(id = "s-deu", language = "deu", label = "Deutsch")

private val audioEnglish = AudioTrack(id = "a-eng", language = "eng", label = "English")
private val audioDutch = AudioTrack(id = "a-nld", language = "nld", label = "Nederlands")

private const val HD_HEIGHT = 720

private val fullHd = QualityLevel(height = 1080, bitrate = 6_000_000, codec = "avc1")
private val hd = QualityLevel(height = HD_HEIGHT, bitrate = 2_500_000, codec = "avc1")
