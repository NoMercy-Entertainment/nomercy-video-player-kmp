// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import tv.nomercy.player.core.device.DeviceCapabilities
import tv.nomercy.player.core.device.FormFactor
import tv.nomercy.player.core.input.PlayerKey
import tv.nomercy.player.core.input.keyCombo
import kotlinx.coroutines.test.runTest
import tv.nomercy.player.testing.FakeVideoBackend
import tv.nomercy.player.video.NMVideoPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// What each key does, checked against the handler it was ported from.
//
// The whole reason this port exists is that a viewer moving between a browser
// and a television finds the same keys doing the same things. A binding that
// drifted would be an error nowhere: it would be a key that behaves differently
// on one client, which is what the two clients being replaced already do.
class VideoKeyHandlerPluginTest {

    private class Device(
        override val formFactor: FormFactor,
        override val hasDpad: Boolean = formFactor == FormFactor.Tv,
        override val hasTouch: Boolean = formFactor == FormFactor.Phone,
        override val hasPointer: Boolean = formFactor == FormFactor.Desktop,
        override val hasHardwareVolumeKeys: Boolean = formFactor != FormFactor.Tv,
        override val hasHdrDisplay: Boolean = false
    ) : DeviceCapabilities

    private var clock: Long = 0

    // Registered on a real player, because that is what installs the defaults.
    // A test that called the installer directly would pass on a plugin nobody
    // could actually use.
    private suspend fun handler(
        commands: RecordingPlayerCommands,
        factor: FormFactor = FormFactor.Desktop,
    ): VideoKeyHandlerPlugin {
        val plugin = VideoKeyHandlerPlugin(commands.commands, Device(factor)) { clock }
        val player = NMVideoPlayer(FakeVideoBackend())
        player.setup()
        player.addPlugin(plugin)
        return plugin
    }

    @Test
    fun spaceIsPlayAndPauseBecauseItAlwaysHasBeen() = runTest {
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        assertTrue(plugin.handle(keyCombo(" ")))
        assertEquals(listOf("togglePlay"), commands.calls)
    }

    @Test
    fun theColourButtonsAreFourDifferentJumps() = runTest {
        // The only keys on a remote nothing else uses, and a remote is where
        // skipping an opening actually matters.
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands, FormFactor.Tv)

        plugin.handle(PlayerKey.ColorRed)
        plugin.handle(PlayerKey.ColorGreen)
        plugin.handle(PlayerKey.ColorYellow)
        plugin.handle(PlayerKey.ColorBlue)

        assertEquals(listOf(30f, 60f, 90f, 120f), commands.seeks)
    }

    @Test
    fun theNumberKeysJumpTheSameAmountsAsTheColours() = runTest {
        // Same four jumps, because a keyboard has no coloured buttons and a
        // viewer should not have to learn two sets.
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("3"))
        plugin.handle(keyCombo("6"))
        plugin.handle(keyCombo("9"))
        plugin.handle(keyCombo("1"))

        assertEquals(listOf(30f, 60f, 90f, 120f), commands.seeks)
    }

    @Test
    fun theModifiersGiveTheSameTwoKeysThreeMagnitudes() = runTest {
        // Someone chasing a line of dialogue wants three seconds; someone
        // skipping a title sequence wants a minute, and neither wants to press
        // one key twenty times.
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("ArrowRight", shift = true))
        plugin.handle(keyCombo("ArrowRight", alt = true))
        plugin.handle(keyCombo("ArrowRight", ctrl = true))
        plugin.handle(keyCombo("ArrowLeft", shift = true))

        assertEquals(listOf(3f, 10f, 60f, -3f), commands.seeks)
    }

    @Test
    fun onATelevisionTheArrowsAreLeftForMovingAround() = runTest {
        // They move focus there. A build that seeked instead makes the controls
        // unreachable, which is the whole reason a television needs different
        // bindings rather than a different player.
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands, FormFactor.Tv)

        assertFalse(plugin.handle(PlayerKey.Right))
        assertEquals(emptyList(), commands.seeks)
    }

    @Test
    fun everywhereElseTheArrowsSeek() = runTest {
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands, FormFactor.Desktop)

        assertTrue(plugin.handle(PlayerKey.Right))
        assertEquals(listOf(10f), commands.seeks)
    }

    @Test
    fun onlyADesktopSpendsTheArrowsOnVolume() = runTest {
        // A phone has volume keys on its side and a television sends volume to
        // the panel, so on both the arrows are better spent elsewhere.
        val phone = RecordingPlayerCommands()
        handler(phone, FormFactor.Phone).handle(PlayerKey.Up)

        val desktop = RecordingPlayerCommands()
        handler(desktop, FormFactor.Desktop).handle(PlayerKey.Up)

        assertEquals(emptyList(), phone.calls)
        assertEquals(listOf("volumeUp"), desktop.calls)
    }

    @Test
    fun theTrackKeysHaveALetterAndARemoteButton() = runTest {
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(PlayerKey.Captions)
        plugin.handle(keyCombo("v"))
        plugin.handle(PlayerKey.AudioTrack)
        plugin.handle(keyCombo("b"))

        assertEquals(listOf("cycleSubtitles", "cycleSubtitles", "cycleAudioTracks", "cycleAudioTracks"), commands.calls)
    }

    @Test
    fun shiftTurnsNextAndPreviousIntoChapters() = runTest {
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("n"))
        plugin.handle(keyCombo("n", shift = true))

        assertEquals(listOf("next", "nextChapter"), commands.calls)
    }

    @Test
    fun speedStepsAlongTheRatesThePlayerOffers() = runTest {
        // Multiplying arrives at rates no engine supports and then fails at the
        // engine rather than here.
        val commands = RecordingPlayerCommands(currentRate = 1f)
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("]"))

        assertEquals(1.25f, commands.rate())
    }

    @Test
    fun speedStopsAtTheEndsRatherThanRunningOffThem() = runTest {
        val commands = RecordingPlayerCommands(currentRate = 2f)
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("]"))

        assertEquals(2f, commands.rate())
    }

    @Test
    fun resettingSpeedGoesStraightBackToNormal() = runTest {
        val commands = RecordingPlayerCommands(currentRate = 1.75f)
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("="))

        assertEquals(1f, commands.rate())
    }

    @Test
    fun aSpeedChangeSaysSoBecauseThereIsNowhereElseToRead() = runTest {
        // On a television there is no status bar. A film that quietly sounds
        // wrong is the failure this avoids.
        val commands = RecordingPlayerCommands(currentRate = 1f)
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("]"))

        assertEquals(listOf("1.25x"), commands.messages)
    }

    @Test
    fun aFrameAdvanceIsRefusedWhileTheFilmIsRunning() = runTest {
        // Advancing a frame while playing is a seek backwards into a film that
        // has already moved past it.
        val commands = RecordingPlayerCommands(playing = true)
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        assertFalse(plugin.handle(keyCombo("e")))
        assertEquals(emptyList(), commands.seeks)
    }

    @Test
    fun aFrameAdvanceWorksWhenPaused() = runTest {
        val commands = RecordingPlayerCommands(playing = false)
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        assertTrue(plugin.handle(keyCombo("e")))
        assertEquals(1, commands.seeks.size)
        assertTrue(commands.seeks.first() > 0f)
    }

    @Test
    fun theTimeKeyShowsElapsedAndRemaining() = runTest {
        val commands = RecordingPlayerCommands()
        commands.at(seconds = 65.0, of = 3725.0)
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("t"))

        assertEquals(listOf("1:05 / -1:01:00"), commands.messages)
    }

    @Test
    fun subtitleSizeIsAnEventRatherThanACall() = runTest {
        // The size of the subtitles belongs to whatever is drawing them, and a
        // key handler that set it directly would be one that knows how they are
        // rendered.
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("+"))
        plugin.handle(keyCombo("-"))

        assertEquals(listOf("subtitle-size-up", "subtitle-size-down"), commands.emitted)
        assertEquals(emptyList(), commands.calls)
    }

    @Test
    fun theQuestionMarkArrivesWithShiftHeld() = runTest {
        // On a standard keyboard the key reports itself as shifted, so a binding
        // written without it is a miss every time. That was worth a paragraph in
        // the reference and it is worth a case here.
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        plugin.handle(keyCombo("+", shift = true))

        assertEquals(listOf("subtitle-size-up"), commands.emitted)
    }

    @Test
    fun aKeyNothingIsBoundToIsLeftAlone() = runTest {
        val commands = RecordingPlayerCommands()
        val plugin: VideoKeyHandlerPlugin = handler(commands)

        assertFalse(plugin.handle(keyCombo("z")))
        assertEquals(emptyList(), commands.calls)
    }
}
