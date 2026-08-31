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
import tv.nomercy.player.core.controllers.InMemoryStorage
import tv.nomercy.player.core.events.AudioTrackPayload
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitlePayload
import tv.nomercy.player.core.events.SubtitlesPayload
import tv.nomercy.player.core.events.ItemChange
import tv.nomercy.player.core.events.TimeUpdate
import tv.nomercy.player.video.NMVideoPlayer
import tv.nomercy.player.video.AudioTracksChange
import tv.nomercy.player.video.VideoEvents
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
        // Its own store, per rig.
        //
        // The default is the PLATFORM's, which is the point of it — a subtitle
        // language has to survive a relaunch. Six tests in this class then wrote
        // over each other's keys in one SharedPreferences file, and two failed on
        // a device while passing on the JVM: "the restore followed the old index"
        // was a rig reading what a neighbouring test had stored.
        val player = NMVideoPlayer(backend = backend, video = backend, storage = InMemoryStorage())
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

    // The one that put an English viewer into Japanese.
    //
    // MediaReady means the source is loaded and will accept a seek; the engine
    // publishes its track list when it has parsed it, which can be after. The
    // restore ran on MediaReady, found no tracks and returned — silently, so the
    // episode played in whatever language the file defaults to. The want has to
    // outlive an empty list.
    @Test
    fun aTrackListThatArrivesAfterMediaReadyStillGetsTheSavedLanguage() = runTest {
        val rig: Rig = rig()
        rig.backend.audio = listOf(audioEnglish, audioDutch)
        rig.player.audioTrack(audioEnglish)
        rig.plugin.awaitWrites()

        // A new item, whose tracks the engine has not parsed yet.
        rig.backend.audio = emptyList()
        rig.player.emit(CoreEvents.Item, ItemChange(rig.player.item(), rig.player.index()))
        rig.player.emit(CoreEvents.MediaReady, Unit)
        rig.plugin.awaitWrites()

        // The engine parses them and starts on the file's own default, which on
        // an anime episode is the Japanese dub and not what the viewer picked.
        // Its own selection is not made through the setter — `audioTrack` is
        // emitted by that setter and by nothing else — so the list changing
        // under the player IS the engine settling on its default.
        rig.backend.audio = listOf(audioJapanese, audioEnglish)
        rig.player.emit(CoreEvents.Time, TimeUpdate(0.5, 1400.0, 0.0))
        rig.plugin.awaitWrites()

        assertEquals(
            "eng",
            rig.player.audioTrack()?.language,
            "the restore gave up on the empty list and never came back",
        )
    }

    // The two lists do not arrive together.
    //
    // One "restore is owed" flag, cleared as soon as the AUDIO list appeared,
    // counted the subtitle restore as answered while the subtitle list was
    // still empty — so captions came back in whatever the file defaults to.
    @Test
    fun aSubtitleListThatArrivesAfterTheAudioListStillGetsTheSavedLanguage() = runTest {
        val rig: Rig = rig()
        rig.backend.subtitleTracks = listOf(english, dutch)
        rig.player.subtitle(dutch)
        rig.plugin.awaitWrites()

        rig.backend.subtitleTracks = emptyList()
        rig.backend.audio = emptyList()
        rig.player.emit(CoreEvents.Item, ItemChange(rig.player.item(), rig.player.index()))
        rig.player.emit(CoreEvents.MediaReady, Unit)
        rig.plugin.awaitWrites()

        // Audio parses first. Subtitles are still a tick behind.
        rig.backend.audio = listOf(audioJapanese, audioEnglish)
        rig.player.emit(CoreEvents.Time, TimeUpdate(0.5, 1400.0, 0.0))
        rig.plugin.awaitWrites()

        // Subtitles parse, and the engine starts on the file's own default. Its
        // own choice is not made through the setter and announces nothing, so it
        // is written straight onto the backend.
        rig.backend.subtitleTracks = listOf(german, english, dutch)
        rig.backend.chosenSubtitle = german
        rig.player.emit(CoreEvents.Time, TimeUpdate(1.0, 1400.0, 0.0))
        rig.plugin.awaitWrites()

        assertEquals(
            "nld",
            rig.player.subtitle()?.language,
            "the audio list answered the subtitle restore and it never came back",
        )
    }

    // A pick the viewer makes is never dropped, whatever order the engine
    // announced things in.
    //
    // The window that keeps the engine's own default out of storage was closed
    // by the track list arriving. An engine that announces its list BEFORE the
    // cursor move re-opened it and closed it never, so every pick after that was
    // dropped as the engine's — a language saved once could not be corrected,
    // and the item switched back to it moments after playback started. Reported
    // from a real phone on 2026-08-31: "it starts on english and then switches
    // after the playback started".
    @Test
    fun aPickMadeAfterTheListWasAnnouncedOutOfOrderIsStillSaved() = runTest {
        val rig: Rig = rig()
        rig.backend.audio = listOf(audioJapanese, audioEnglish)
        rig.player.audioTrack(audioJapanese)
        rig.plugin.awaitWrites()

        // The cursor moves while the engine has nothing parsed yet, so the
        // restore this item is owed cannot run and goes on being owed.
        rig.backend.audio = emptyList()
        rig.player.emit(CoreEvents.Item, ItemChange(rig.player.item(), rig.player.index()))
        rig.plugin.awaitWrites()

        // The list appears without an announcement, and the viewer picks English
        // from it. A guard that waits for the restore before it will trust a
        // selection drops this one and keeps the old language for ever.
        rig.backend.audio = listOf(audioJapanese, audioEnglish)
        rig.player.audioTrack(audioEnglish)
        rig.plugin.awaitWrites()
        assertEquals("eng", rig.plugin.savedAudioLanguage(), "the viewer's pick was never written down")

        // The engine announces the new item's list, and only then does the cursor
        // move arrive.
        rig.player.emit(VideoEvents.AudioTracks, AudioTracksChange(emptyList()))
        rig.plugin.awaitWrites()
        rig.player.emit(CoreEvents.Item, ItemChange(rig.player.item(), rig.player.index()))
        // The item has settled before anyone touches the menu.
        rig.plugin.awaitWrites()

        // The viewer opens the menu and picks English.
        rig.player.audioTrack(audioEnglish)
        rig.plugin.awaitWrites()
        rig.plugin.awaitWrites()

        // A new item, whose engine starts on its own default.
        rig.player.emit(CoreEvents.Item, ItemChange(rig.player.item(), rig.player.index()))
        rig.backend.audio = listOf(audioJapanese, audioEnglish)
        assertEquals("eng", rig.player.audioTrack()?.language, "STAGE-tracks")
        rig.player.emit(CoreEvents.MediaReady, Unit)
        rig.plugin.awaitWrites()
        assertEquals("eng", rig.player.audioTrack()?.language, "STAGE-ready")

        assertEquals(
            "eng",
            rig.player.audioTrack()?.language,
            "the viewer's own pick was dropped as the engine's default",
        )
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

    // An episode that ENDS hands the next one a list that has not gone away.
    //
    // Reported from the living-room television on 2026-08-31: the next episode
    // started in Japanese while English was playing. On an auto-advance the
    // cursor moves while the outgoing item is still loaded, so the engine still
    // answers with ITS track list — the restore ran against the wrong item's
    // list, matched there, and counted itself answered. The new item's own list
    // then arrived with nothing owed against it.
    @Test
    fun theNextEpisodeGetsTheRestoreEvenThoughTheOldListWasStillThere() = runTest {
        val rig: Rig = rig()
        rig.backend.audio = listOf(audioJapanese, audioEnglish)
        rig.player.audioTrack(audioEnglish)
        rig.plugin.awaitWrites()

        // The cursor moves to the next episode. Nothing has been torn down yet,
        // so the outgoing item's tracks are still what the engine reports.
        rig.player.emit(CoreEvents.Item, ItemChange(rig.player.item(), rig.player.index()))
        rig.plugin.awaitWrites()

        // The new source finishes loading and the engine settles on the dub the
        // file declares. Set on the backend, not through the setter: an engine
        // choosing its own default announces nothing.
        rig.backend.chosenAudio = audioJapanese
        // The payload is the announcement; the plugin reads the list off the
        // player, which is where the new item's tracks now are.
        rig.player.emit(VideoEvents.AudioTracks, AudioTracksChange(emptyList()))
        // Joined per event: `remember` keeps only the LAST handle, so awaiting
        // once after both emits can return while the first is still running.
        rig.plugin.awaitWrites()
        rig.player.emit(CoreEvents.MediaReady, Unit)
        rig.plugin.awaitWrites()
        assertEquals(
            "eng",
            rig.player.audioTrack()?.language,
            "the next episode opened in the file's dub, so the restore was spent on the old item's list",
        )
    }

    // An engine that never announces its list must not be told the same thing
    // on every tick: the setter announces, and the chrome puts a notice on the
    // picture for each one — which is a track name that never goes away.
    @Test
    fun aTickDoesNotReSelectTheLanguageThatIsAlreadyPlaying() = runTest {
        val rig: Rig = rig()
        rig.backend.audio = listOf(audioJapanese, audioEnglish)
        rig.player.audioTrack(audioEnglish)
        rig.plugin.awaitWrites()

        var announced = 0
        rig.player.on(CoreEvents.AudioTrack) { announced++ }

        rig.player.emit(CoreEvents.Item, ItemChange(rig.player.item(), rig.player.index()))
        rig.plugin.awaitWrites()
        repeat(3) {
            rig.player.emit(CoreEvents.Time, TimeUpdate(it.toDouble(), 1400.0, 0.0))
            rig.plugin.awaitWrites()
        }

        assertEquals(0, announced, "the track already playing was selected again, once per tick")
    }

    // What the viewer picked is what gets written down.
    //
    // A selection reaches the engine asynchronously — Media3 applies it on the
    // main thread and refreshes its cache after — so the track the player
    // reports while the event is being handled is still the OLD one. Saving
    // that stored the language being replaced, and the list announcement the
    // switch produces then restored it: the language changed back by itself.
    @Test
    fun theLanguageSavedIsTheOneChosenAndNotTheOneStillPlaying() = runTest {
        val rig: Rig = rig()
        rig.backend.audio = listOf(audioJapanese, audioEnglish)
        rig.backend.chosenAudio = audioJapanese
        rig.plugin.awaitWrites()

        // The engine has not caught up: it still answers Japanese while the
        // event names English at index one.
        rig.player.emit(CoreEvents.AudioTrack, AudioTrackPayload(id = 1.0))
        rig.plugin.awaitWrites()

        assertEquals(
            "eng",
            rig.plugin.savedAudioLanguage(),
            "the language being replaced was saved instead of the one chosen",
        )
    }

    // The caption half of the same lag.
    @Test
    fun theSubtitleSavedIsTheOneChosenAndNotTheOneStillShowing() = runTest {
        val rig: Rig = rig()
        rig.backend.subtitleTracks = listOf(english, dutch)
        rig.backend.chosenSubtitle = english
        rig.plugin.awaitWrites()

        rig.player.emit(CoreEvents.Subtitle, SubtitlePayload(track = 1.0))
        rig.plugin.awaitWrites()

        assertEquals(
            "nld",
            rig.plugin.savedSubtitle()?.language,
            "the caption being replaced was saved instead of the one chosen",
        )
    }

    // A switch is not a cursor move, and only a cursor move owes a restore.
    //
    // Every track switch makes the engine publish its list again. With nothing
    // owed, that announcement still ran the restore, which re-selected against a
    // player mid-switch — an extra selection per switch, which is the chop and
    // the flip back a viewer sees.
    @Test
    fun aListAnnouncedAfterTheItemSettledSelectsNothing() = runTest {
        val rig: Rig = rig()
        rig.backend.audio = listOf(audioJapanese, audioEnglish)
        rig.player.audioTrack(audioEnglish)
        rig.plugin.awaitWrites()

        // The item settles: the want is raised and the announcement answers it.
        rig.player.emit(CoreEvents.Item, ItemChange(rig.player.item(), rig.player.index()))
        rig.plugin.awaitWrites()
        rig.player.emit(VideoEvents.AudioTracks, AudioTracksChange(emptyList()))
        rig.plugin.awaitWrites()

        var announced = 0
        rig.player.on(CoreEvents.AudioTrack) { announced++ }

        // The viewer switches to Japanese, and the engine publishes its list
        // again while it is still reporting English.
        rig.backend.chosenAudio = audioEnglish
        rig.plugin.rememberAudioLanguage("jpn")
        rig.player.emit(VideoEvents.AudioTracks, AudioTracksChange(emptyList()))
        rig.plugin.awaitWrites()

        assertEquals(0, announced, "the announcement after a switch selected a track of its own")
    }

    // And the caption half of that one.
    @Test
    fun aSubtitleListAnnouncedAfterTheItemSettledSelectsNothing() = runTest {
        val rig: Rig = rig()
        rig.backend.subtitleTracks = listOf(english, dutch)
        rig.player.subtitle(english)
        rig.plugin.awaitWrites()

        rig.player.emit(CoreEvents.Item, ItemChange(rig.player.item(), rig.player.index()))
        rig.plugin.awaitWrites()
        rig.player.emit(CoreEvents.Subtitles, SubtitlesPayload(emptyList()))
        rig.plugin.awaitWrites()

        // The viewer switches to Dutch, and the engine has not caught up: it
        // still reports English while the list is published again.
        rig.player.subtitle(dutch)
        rig.plugin.awaitWrites()
        rig.backend.chosenSubtitle = english

        var announced = 0
        rig.player.on(CoreEvents.Subtitle) { announced++ }

        rig.player.emit(CoreEvents.Subtitles, SubtitlesPayload(emptyList()))
        rig.plugin.awaitWrites()

        assertEquals(0, announced, "the announcement after a switch selected a caption of its own")
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

// The dub an anime file declares as its default, which is what the engine
// starts on before anybody's preference is applied.
private val audioJapanese = AudioTrack(id = "a-jpn", language = "jpn", label = "日本語")

private const val HD_HEIGHT = 720

private val fullHd = QualityLevel(height = 1080, bitrate = 6_000_000, codec = "avc1")
private val hd = QualityLevel(height = HD_HEIGHT, bitrate = 2_500_000, codec = "avc1")
