// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import kotlinx.coroutines.test.runTest
import tv.nomercy.player.core.device.DeviceCapabilities
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.core.input.keyCombo
import tv.nomercy.player.core.media.Chapter
import tv.nomercy.player.testing.FakeVideoBackend
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The bindings a remote needs that a keyboard does not.
//
// Only the groups that differ are overridden, so half of what matters here is
// what did not change: the coloured buttons and the media keys have to still be
// the inherited ones, or a television is running a second copy of the bindings
// that can drift from the first.
class TvKeyHandlerPluginTest {

    private class Television : DeviceCapabilities {
        override val formFactor: FormFactor = FormFactor.Tv
        override val hasDpad: Boolean = true
        override val hasTouch: Boolean = false
        override val hasPointer: Boolean = false
        override val hasHardwareVolumeKeys: Boolean = false
        override val hasHdrDisplay: Boolean = false
    }

    private var clock: Long = 0

    private suspend fun handler(
        commands: RecordingPlayerCommands,
        options: TvKeyHandlerOptions = TvKeyHandlerOptions(),
    ): TvKeyHandlerPlugin {
        val plugin = TvKeyHandlerPlugin(commands.commands, Television(), { clock }, options)
        val player = NMVideoPlayer(FakeVideoBackend())
        player.setup()
        player.addPlugin(plugin)
        return plugin
    }

    @Test
    fun theArrowsSeekOnARemoteEvenThoughTheyDoNotOnATelevisionElsewhere() = runTest {
        // The base handler leaves the arrows alone on a television because they
        // move focus. A remote with the chrome closed has nothing to move, so
        // this is where they become a seek.
        val commands = RecordingPlayerCommands()
        val plugin: TvKeyHandlerPlugin = handler(commands)

        plugin.handle(PlayerKey.Right)
        plugin.handle(PlayerKey.Left)

        assertEquals(listOf(5f, -5f), commands.seeks)
    }

    @Test
    fun theStepIsWhateverTheHostConfigured() = runTest {
        val commands = RecordingPlayerCommands()
        val plugin: TvKeyHandlerPlugin = handler(commands, TvKeyHandlerOptions(arrowSeekSeconds = 8))

        plugin.handle(PlayerKey.Right)
        plugin.handle(PlayerKey.Left)

        assertEquals(listOf(8f, -8f), commands.seeks)
    }

    @Test
    fun theVolumeArrowsWorkHereBecauseSomeBoxesDoDeliverThem() = runTest {
        // The base handler spends them only on a desktop. Some set-top boxes
        // pass their volume keys to the running application rather than to the
        // panel, and on those these arrows are the only volume control there is.
        val commands = RecordingPlayerCommands()
        val plugin: TvKeyHandlerPlugin = handler(commands)

        plugin.handle(PlayerKey.Up)
        plugin.handle(PlayerKey.Down)

        assertEquals(listOf("volumeUp", "volumeDown"), commands.calls)
    }

    @Test
    fun theColourButtonsAreStillTheInheritedOnes() = runTest {
        // The half that matters most. A television running its own copy of these
        // is a television whose coloured buttons can drift from every other
        // client, which is exactly the failure this port exists to end.
        val commands = RecordingPlayerCommands()
        val plugin: TvKeyHandlerPlugin = handler(commands)

        plugin.handle(PlayerKey.ColorRed)
        plugin.handle(PlayerKey.ColorBlue)

        assertEquals(listOf(30f, 120f), commands.seeks)
    }

    @Test
    fun theMediaKeysAreStillTheInheritedOnesToo() = runTest {
        val commands = RecordingPlayerCommands()
        val plugin: TvKeyHandlerPlugin = handler(commands)

        plugin.handle(PlayerKey.MediaPlayPause)
        plugin.handle(PlayerKey.Captions)

        assertEquals(listOf("togglePlay", "cycleSubtitles"), commands.calls)
    }

    @Test
    fun theInfoButtonAsksWhoeverIsListeningRatherThanDrawing() = runTest {
        // It has to work on a television with no chrome mounted at all, and a
        // handler that drew a panel would break the moment somebody embedded the
        // player bare.
        val commands = RecordingPlayerCommands()
        commands.at(seconds = 90.0, of = 3600.0)
        val player = NMVideoPlayer(FakeVideoBackend())
        val seen: MutableList<TvPlaybackSummary> = mutableListOf()
        player.on(TvKeyEvents.InfoOnPlayer) { seen += it }
        player.setup()
        val plugin = TvKeyHandlerPlugin(commands.commands, Television(), { clock })
        player.addPlugin(plugin)

        plugin.handle(PlayerKey.Info)

        assertEquals(1, seen.size)
        assertEquals(90.0, seen.first().timeSeconds)
        assertEquals(3600.0, seen.first().durationSeconds)
    }

    @Test
    fun theInfoPanelIsToldWhichChapterAndHowMuchIsLeft() = runTest {
        // The two numbers were all this sent. On a television that is the only
        // place a viewer can read either, so a film with chapters announced a
        // position and left them to work out where in the film it was.
        val commands = RecordingPlayerCommands()
        commands.at(seconds = 700.0, of = 3600.0)
        commands.withChapters(
            listOf(
                Chapter(startTime = 0.0, title = "Titles"),
                Chapter(startTime = 600.0, title = "The Crossing"),
            ),
        )
        val player = NMVideoPlayer(FakeVideoBackend())
        val seen: MutableList<TvPlaybackSummary> = mutableListOf()
        player.on(TvKeyEvents.InfoOnPlayer) { seen += it }
        player.setup()
        val plugin = TvKeyHandlerPlugin(commands.commands, Television(), { clock })
        player.addPlugin(plugin)

        plugin.handle(PlayerKey.Info)

        assertEquals("Chapter 2: The Crossing", seen.first().chapterLabel)
        assertEquals(2900.0, seen.first().remainingSeconds)
    }

    @Test
    fun theChapterWordOnThePanelComesFromTheViewersLocale() = runTest {
        // Asked for by locale rather than handed in as a string, which is the
        // whole difference the table makes: the word is the one the web plugin
        // already ships for Dutch, not one this test invented for it.
        val commands = RecordingPlayerCommands()
        commands.at(seconds = 700.0, of = 3600.0)
        commands.withChapters(listOf(Chapter(startTime = 600.0, title = "De Oversteek")))
        val player = NMVideoPlayer(FakeVideoBackend())
        val seen: MutableList<TvPlaybackSummary> = mutableListOf()
        player.on(TvKeyEvents.InfoOnPlayer) { seen += it }
        player.setup()
        val plugin = TvKeyHandlerPlugin(
            commands.commands,
            Television(),
            { clock },
            TvKeyHandlerOptions(locale = "nl"),
        )
        player.addPlugin(plugin)

        plugin.handle(PlayerKey.Info)

        assertEquals("Hoofdstuk 1: De Oversteek", seen.first().chapterLabel)
    }

    @Test
    fun anUnnamedItemGetsTheWordForNothingNamedInTheViewersLocale() = runTest {
        // A server sending an empty title is not a server naming the item "", and
        // a panel whose first line is blank reads as a panel that failed to load.
        val commands = RecordingPlayerCommands()
        val player = NMVideoPlayer(FakeVideoBackend())
        val seen: MutableList<TvPlaybackSummary> = mutableListOf()
        player.on(TvKeyEvents.InfoOnPlayer) { seen += it }
        player.setup()
        val plugin = TvKeyHandlerPlugin(
            commands.commands,
            Television(),
            { clock },
            TvKeyHandlerOptions(locale = "nl"),
        )
        player.addPlugin(plugin)

        plugin.handle(PlayerKey.Info)

        assertEquals("Geen titel", seen.first().title)
    }

    @Test
    fun theRecordButtonMarksTheSpotItWasPressedAt() = runTest {
        // Record on a remote has no recorder to talk to. It is the one button
        // with nothing else to do, and somebody reaching for it is already
        // thinking about coming back here.
        val commands = RecordingPlayerCommands()
        commands.at(seconds = 1234.5, of = 3600.0)
        val player = NMVideoPlayer(FakeVideoBackend())
        val marks: MutableList<TvBookmark> = mutableListOf()
        player.on(TvKeyEvents.BookmarkOnPlayer) { marks += it }
        player.setup()
        val plugin = TvKeyHandlerPlugin(commands.commands, Television(), { clock })
        player.addPlugin(plugin)

        plugin.handle(PlayerKey.MediaRecord)

        assertEquals(listOf(1234.5), marks.map { it.timeSeconds })
    }

    @Test
    fun changingTheShapeOfThePictureSaysSo() = runTest {
        // There is no window title and no status bar on a television. A picture
        // that changes shape with no explanation reads as the player breaking
        // rather than as a setting changing.
        val commands = RecordingPlayerCommands()
        val plugin: TvKeyHandlerPlugin = handler(commands)

        plugin.handle(PlayerKey.Favorites)

        assertTrue(commands.calls.contains("cycleAspectRatio"))
        // The web plugin's own English, not a rewording of it. This said
        // "aspect ratio changed", which is a fourth spelling of a string the web
        // already had in 79 languages.
        assertEquals(listOf("Aspect ratio"), commands.messages)
    }

    @Test
    fun theShapeMessageIsAnnouncedInTheViewersLocale() = runTest {
        val commands = RecordingPlayerCommands()
        val plugin: TvKeyHandlerPlugin = handler(commands, TvKeyHandlerOptions(locale = "nl"))

        plugin.handle(PlayerKey.Favorites)

        assertEquals(listOf("Beeldverhouding"), commands.messages)
    }

    @Test
    fun theKeyboardShortcutStillWorksForABoxWithAKeyboardAttached() = runTest {
        // Plenty of set-top boxes have one plugged in, and the letter bindings
        // are inherited rather than removed.
        val commands = RecordingPlayerCommands()
        val plugin: TvKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("a"))

        assertEquals(listOf("cycleAspectRatio"), commands.calls)
    }
}
